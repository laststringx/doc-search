package com.enterprise.documentsearch.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.AsyncBucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Enterprise-grade rate limiting service using Bucket4j with Redis backend
 * Supports per-tenant and global rate limiting for 1000+ concurrent requests
 */
@Service
@Slf4j
public class RateLimitingService {

    private final ProxyManager<String> proxyManager;
    private final ConcurrentMap<String, AsyncBucketProxy> bucketCache = new ConcurrentHashMap<>();

    @Value("${app.search.rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled;

    @Value("${app.search.rate-limiting.per-tenant.capacity:1000}")
    private long perTenantCapacity;

    @Value("${app.search.rate-limiting.per-tenant.refill-tokens:100}")
    private long perTenantRefillTokens;

    @Value("${app.search.rate-limiting.per-tenant.refill-period:1s}")
    private Duration perTenantRefillPeriod;

    @Value("${app.search.rate-limiting.global.capacity:5000}")
    private long globalCapacity;

    @Value("${app.search.rate-linking.global.refill-tokens:500}")
    private long globalRefillTokens;

    @Value("${app.search.rate-limiting.global.refill-period:1s}")
    private Duration globalRefillPeriod;

    @Autowired
    public RateLimitingService(RedisClient redisClient) {
        StatefulRedisConnection<String, byte[]> redisConnection = redisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
        );
        this.proxyManager = LettuceBasedProxyManager.builderFor(redisConnection)
                .withExpirationAfterWriteStrategy(Duration.ofMinutes(10))
                .build();
    }

    /**
     * Check if request is allowed for specific tenant
     */
    public Boolean isAllowed(String tenantId, String userId) {
        if (!rateLimitingEnabled) {
            return true;
        }

        try {
            boolean tenantAllowed = checkTenantRateLimit(tenantId);
            if (!tenantAllowed) {
                return false;
            }
            
            boolean userAllowed = checkUserRateLimit(tenantId, userId);
            if (!userAllowed) {
                return false;
            }
            
            return checkGlobalRateLimit();
        } catch (Exception e) {
            log.warn("Rate limiting check failed for tenant: {}, user: {}, error: {}", tenantId, userId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if search request is allowed for tenant
     */
    public Boolean isSearchAllowed(String tenantId, String userId) {
        if (!rateLimitingEnabled) {
            return true;
        }

        try {
            String searchKey = "search:" + tenantId;
            String userSearchKey = "user-search:" + tenantId + ":" + userId;

            boolean tenantAllowed = getTenantSearchBucket(searchKey).tryConsume(1).get();
            if (!tenantAllowed) {
                return false;
            }
            
            return getUserSearchBucket(userSearchKey).tryConsume(1).get();
        } catch (Exception e) {
            log.warn("Search rate limiting check failed for tenant: {}, user: {}, error: {}", 
                    tenantId, userId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if file upload is allowed for tenant
     */
    public Boolean isUploadAllowed(String tenantId, String userId, long fileSize) {
        if (!rateLimitingEnabled) {
            return true;
        }

        try {
            String uploadKey = "upload:" + tenantId;
            String userUploadKey = "user-upload:" + tenantId + ":" + userId;

            // Calculate tokens needed based on file size (1 token per MB)
            long tokensNeeded = Math.max(1, fileSize / (1024 * 1024));

            boolean tenantAllowed = getTenantUploadBucket(uploadKey).tryConsume(tokensNeeded).get();
            if (!tenantAllowed) {
                return false;
            }
            
            return getUserUploadBucket(userUploadKey).tryConsume(tokensNeeded).get();
        } catch (Exception e) {
            log.warn("Upload rate limiting check failed for tenant: {}, user: {}, error: {}", 
                    tenantId, userId, e.getMessage());
            return false;
        }
    }

    /**
     * Get remaining tokens for tenant
     */
    public Long getRemainingTokens(String tenantId) {
        try {
            String key = "tenant:" + tenantId;
            return getTenantBucket(key).getAvailableTokens().get();
        } catch (Exception e) {
            log.warn("Failed to get remaining tokens for tenant: {}, error: {}", tenantId, e.getMessage());
            return 0L;
        }
    }

    /**
     * Get rate limit status for tenant
     */
    public RateLimitStatus getRateLimitStatus(String tenantId, String userId) {
        try {
            String tenantKey = "tenant:" + tenantId;
            String userKey = "user:" + tenantId + ":" + userId;
            String globalKey = "global";

            long tenantTokens = getTenantBucket(tenantKey).getAvailableTokens().get();
            long userTokens = getUserBucket(userKey).getAvailableTokens().get();
            long globalTokens = getGlobalBucket(globalKey).getAvailableTokens().get();

            return new RateLimitStatus(
                    tenantTokens,
                    userTokens,
                    globalTokens,
                    perTenantCapacity,
                    100L, // User capacity
                    globalCapacity
            );
        } catch (Exception e) {
            log.warn("Failed to get rate limit status for tenant: {}, user: {}, error: {}", 
                    tenantId, userId, e.getMessage());
            return new RateLimitStatus(0L, 0L, 0L, perTenantCapacity, 100L, globalCapacity);
        }
    }

    /**
     * Reset rate limits for tenant (admin operation)
     */
    public void resetTenantLimits(String tenantId) {
        try {
            String tenantKey = "tenant:" + tenantId;
            bucketCache.remove(tenantKey);
            // Redis bucket will be recreated on next access
            log.info("Reset rate limits for tenant: {}", tenantId);
        } catch (Exception e) {
            log.warn("Failed to reset rate limits for tenant: {}, error: {}", tenantId, e.getMessage());
        }
    }

    /**
     * Set custom rate limit for tenant
     */
    public void setCustomTenantLimit(String tenantId, long capacity, long refillTokens, Duration refillPeriod) {
        try {
            String key = "custom-tenant:" + tenantId;
            
            BucketConfiguration configuration = BucketConfiguration.builder()
                    .addLimit(Bandwidth.simple(capacity, refillPeriod).withInitialTokens(refillTokens))
                    .build();

            AsyncBucketProxy bucket = proxyManager.builder().build(key, configuration);
            bucketCache.put(key, bucket);
            
            log.info("Set custom rate limit for tenant: {} - capacity: {}, refill: {} tokens per {}", 
                    tenantId, capacity, refillTokens, refillPeriod);
        } catch (Exception e) {
            log.warn("Failed to set custom rate limit for tenant: {}, error: {}", tenantId, e.getMessage());
        }
    }

    private boolean checkTenantRateLimit(String tenantId) {
        try {
            String key = "tenant:" + tenantId;
            return getTenantBucket(key).tryConsume(1).get();
        } catch (Exception e) {
            log.warn("Tenant rate limit check failed for: {}, error: {}", tenantId, e.getMessage());
            return false;
        }
    }

    private boolean checkUserRateLimit(String tenantId, String userId) {
        try {
            String key = "user:" + tenantId + ":" + userId;
            return getUserBucket(key).tryConsume(1).get();
        } catch (Exception e) {
            log.warn("User rate limit check failed for tenant: {}, user: {}, error: {}", 
                    tenantId, userId, e.getMessage());
            return false;
        }
    }

    private boolean checkGlobalRateLimit() {
        try {
            String key = "global";
            return getGlobalBucket(key).tryConsume(1).get();
        } catch (Exception e) {
            log.warn("Global rate limit check failed, error: {}", e.getMessage());
            return false;
        }
    }

    private AsyncBucketProxy getTenantBucket(String key) {
        return bucketCache.computeIfAbsent(key, k -> {
            BucketConfiguration configuration = BucketConfiguration.builder()
                    .addLimit(Bandwidth.simple(perTenantCapacity, perTenantRefillPeriod)
                            .withInitialTokens(perTenantRefillTokens))
                    .build();
            return proxyManager.builder().build(k, configuration);
        });
    }

    private AsyncBucketProxy getUserBucket(String key) {
        return bucketCache.computeIfAbsent(key, k -> {
            // User limit: 100 requests per minute
            BucketConfiguration configuration = BucketConfiguration.builder()
                    .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1))
                            .withInitialTokens(100))
                    .build();
            return proxyManager.builder().build(k, configuration);
        });
    }

    private AsyncBucketProxy getGlobalBucket(String key) {
        return bucketCache.computeIfAbsent(key, k -> {
            BucketConfiguration configuration = BucketConfiguration.builder()
                    .addLimit(Bandwidth.simple(globalCapacity, globalRefillPeriod)
                            .withInitialTokens(globalRefillTokens))
                    .build();
            return proxyManager.builder().build(k, configuration);
        });
    }

    private AsyncBucketProxy getTenantSearchBucket(String key) {
        return bucketCache.computeIfAbsent(key, k -> {
            // Search-specific limits: 500 searches per minute per tenant
            BucketConfiguration configuration = BucketConfiguration.builder()
                    .addLimit(Bandwidth.simple(500, Duration.ofMinutes(1))
                            .withInitialTokens(500))
                    .build();
            return proxyManager.builder().build(k, configuration);
        });
    }

    private AsyncBucketProxy getUserSearchBucket(String key) {
        return bucketCache.computeIfAbsent(key, k -> {
            // User search limits: 50 searches per minute per user
            BucketConfiguration configuration = BucketConfiguration.builder()
                    .addLimit(Bandwidth.simple(50, Duration.ofMinutes(1))
                            .withInitialTokens(50))
                    .build();
            return proxyManager.builder().build(k, configuration);
        });
    }

    private AsyncBucketProxy getTenantUploadBucket(String key) {
        return bucketCache.computeIfAbsent(key, k -> {
            // Upload limits: 1GB per hour per tenant (1024 tokens)
            BucketConfiguration configuration = BucketConfiguration.builder()
                    .addLimit(Bandwidth.simple(1024, Duration.ofHours(1))
                            .withInitialTokens(1024))
                    .build();
            return proxyManager.builder().build(k, configuration);
        });
    }

    private AsyncBucketProxy getUserUploadBucket(String key) {
        return bucketCache.computeIfAbsent(key, k -> {
            // User upload limits: 100MB per hour per user (100 tokens)
            BucketConfiguration configuration = BucketConfiguration.builder()
                    .addLimit(Bandwidth.simple(100, Duration.ofHours(1))
                            .withInitialTokens(100))
                    .build();
            return proxyManager.builder().build(k, configuration);
        });
    }

    /**
     * Rate limit status record
     */
    public record RateLimitStatus(
            long tenantTokensRemaining,
            long userTokensRemaining,
            long globalTokensRemaining,
            long tenantCapacity,
            long userCapacity,
            long globalCapacity
    ) {
        public boolean isAllowed() {
            return tenantTokensRemaining > 0 && userTokensRemaining > 0 && globalTokensRemaining > 0;
        }

        public double tenantUtilization() {
            return 1.0 - ((double) tenantTokensRemaining / tenantCapacity);
        }

        public double userUtilization() {
            return 1.0 - ((double) userTokensRemaining / userCapacity);
        }

        public double globalUtilization() {
            return 1.0 - ((double) globalTokensRemaining / globalCapacity);
        }
    }
}