package com.enterprise.documentsearch.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Multi-layer caching service for enterprise document search
 * L1: Caffeine in-memory cache for ultra-fast access
 * L2: Redis cluster for distributed caching
 */
@Service
@Slf4j
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Cache<String, Object> l1Cache;

    @Value("${app.search.cache.l1.max-size:10000}")
    private int l1MaxSize;

    @Value("${app.search.cache.l1.expire-after-write:5m}")
    private Duration l1ExpireAfterWrite;

    @Value("${app.search.cache.l2.expire-after-write:30m}")
    private Duration l2ExpireAfterWrite;

    @Value("${app.search.cache.l2.key-prefix:doc-search:}")
    private String keyPrefix;

    @Autowired
    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(l1MaxSize)
                .expireAfterWrite(l1ExpireAfterWrite)
                .build();
    }

    /**
     * Get value from cache with L1 -> L2 fallback
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        try {
            // Try L1 cache first
            T l1Value = (T) l1Cache.getIfPresent(key);
            if (l1Value != null) {
                return l1Value;
            }

            // Try L2 cache (Redis cluster)
            String redisKey = keyPrefix + key;
            Object redisValue = redisTemplate.opsForValue().get(redisKey);
            
            if (redisValue != null && type.isInstance(redisValue)) {
                T value = type.cast(redisValue);
                // Populate L1 cache for future requests
                l1Cache.put(key, value);
                return value;
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Cache get failed for key: {}, error: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Put value into both cache layers
     */
    public <T> void put(String key, T value) {
        try {
            // Store in L1 cache
            l1Cache.put(key, value);

            // Store in L2 cache (Redis cluster)  
            String redisKey = keyPrefix + key;
            redisTemplate.opsForValue().set(redisKey, value, l2ExpireAfterWrite);
        } catch (Exception e) {
            log.warn("Cache put failed for key: {}, error: {}", key, e.getMessage());
        }
    }

    /**
     * Put value with custom TTL
     */
    public <T> void put(String key, T value, Duration ttl) {
        try {
            // Store in L1 cache
            l1Cache.put(key, value);

            // Store in L2 cache with custom TTL
            String redisKey = keyPrefix + key;
            redisTemplate.opsForValue().set(redisKey, value, ttl);
        } catch (Exception e) {
            log.warn("Cache put with TTL failed for key: {}, error: {}", key, e.getMessage());
        }
    }

    /**
     * Get or compute value if not present
     */
    public <T> T getOrCompute(String key, Class<T> type, CompletableFuture<T> computeFunction) {
        try {
            T cached = get(key, type);
            if (cached != null) {
                return cached;
            }
            
            T computed = computeFunction.get();
            if (computed != null) {
                put(key, computed);
            }
            return computed;
        } catch (Exception e) {
            log.warn("Cache getOrCompute failed for key: {}, error: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Invalidate key from both cache layers
     */
    public void evict(String key) {
        try {
            // Remove from L1 cache
            l1Cache.invalidate(key);

            // Remove from L2 cache
            String redisKey = keyPrefix + key;
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("Cache evict failed for key: {}, error: {}", key, e.getMessage());
        }
    }

    /**
     * Bulk eviction for multiple keys
     */
    public void evictAll(List<String> keys) {
        try {
            // Remove from L1 cache
            keys.forEach(l1Cache::invalidate);

            // Remove from L2 cache
            List<String> redisKeys = keys.stream()
                    .map(key -> keyPrefix + key)
                    .toList();

            redisTemplate.delete(redisKeys.toArray(new String[0]));
        } catch (Exception e) {
            log.warn("Cache bulk evict failed, error: {}", e.getMessage());
        }
    }

    /**
     * Clear all cache entries for a tenant
     */
    public void evictTenant(String tenantId) {
        try {
            String pattern = keyPrefix + "tenant:" + tenantId + ":*";
            
            // Note: keys() operation can be expensive in production
            // Consider using SCAN command for large datasets
            var keys = redisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                // Remove from L1 cache
                keys.forEach(redisKey -> {
                    String cacheKey = redisKey.substring(keyPrefix.length());
                    l1Cache.invalidate(cacheKey);
                });

                // Remove from L2 cache
                redisTemplate.delete(keys.toArray(new String[0]));
            }
        } catch (Exception e) {
            log.warn("Tenant cache eviction failed for tenant: {}, error: {}", tenantId, e.getMessage());
        }
    }

    /**
     * Warm up cache with frequently accessed data
     */
    public void warmUpCache(String tenantId, List<String> searchTerms) {
        try {
            for (String term : searchTerms) {
                String cacheKey = "search:" + tenantId + ":" + term;
                // Pre-load common search results
                get(cacheKey, Object.class);
            }
        } catch (Exception e) {
            log.warn("Cache warm-up failed for tenant: {}, error: {}", tenantId, e.getMessage());
        }
    }

    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        try {
            var caffeineStats = l1Cache.stats();
            
            // Get Redis info - simplified version for compatibility
            String redisInfo = "Redis cluster info";
            
            return new CacheStats(
                    l1Cache.estimatedSize(),
                    caffeineStats.hitRate(),
                    caffeineStats.evictionCount(),
                    redisInfo
            );
        } catch (Exception e) {
            log.warn("Failed to get cache stats: {}", e.getMessage());
            return new CacheStats(0, 0.0, 0, "Stats unavailable");
        }
    }

    /**
     * Cache statistics record
     */
    public record CacheStats(
            long l1Size,
            double l1HitRate,
            long l1Evictions,
            String redisMemoryInfo
    ) {}

    /**
     * Check if key exists in cache
     */
    public boolean exists(String key) {
        try {
            // Check L1 first
            if (l1Cache.getIfPresent(key) != null) {
                return true;
            }

            // Check L2
            String redisKey = keyPrefix + key;
            return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
        } catch (Exception e) {
            log.warn("Cache exists check failed for key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * Set expiration for existing key
     */
    public void expire(String key, Duration ttl) {
        try {
            String redisKey = keyPrefix + key;
            redisTemplate.expire(redisKey, ttl);
        } catch (Exception e) {
            log.warn("Cache expire failed for key: {}, error: {}", key, e.getMessage());
        }
    }

    /**
     * Get remaining TTL for key
     */
    public Duration getTtl(String key) {
        try {
            String redisKey = keyPrefix + key;
            return redisTemplate.getExpire(redisKey);
        } catch (Exception e) {
            log.warn("Cache TTL check failed for key: {}, error: {}", key, e.getMessage());
            return Duration.ZERO;
        }
    }

    /**
     * Increment counter in cache
     */
    public Long increment(String key) {
        try {
            String redisKey = keyPrefix + key;
            return redisTemplate.opsForValue().increment(redisKey);
        } catch (Exception e) {
            log.warn("Cache increment failed for key: {}, error: {}", key, e.getMessage());
            return 0L;
        }
    }

    /**
     * Increment counter with expiration
     */
    public Long increment(String key, Duration expiration) {
        try {
            String redisKey = keyPrefix + key;
            Long count = redisTemplate.opsForValue().increment(redisKey);
            
            if (count != null && count == 1) {
                // Set expiration only for new keys
                redisTemplate.expire(redisKey, expiration);
            }
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("Cache increment with expiration failed for key: {}, error: {}", key, e.getMessage());
            return 0L;
        }
    }

    /**
     * Store search results with optimized serialization
     */
    public <T> void putSearchResults(String tenantId, String query, List<T> results, int page) {
        String cacheKey = "search:" + tenantId + ":" + query + ":page:" + page;
        put(cacheKey, results);
    }

    /**
     * Get cached search results
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getSearchResults(String tenantId, String query, int page, Class<T> type) {
        String cacheKey = "search:" + tenantId + ":" + query + ":page:" + page;
        List<?> list = get(cacheKey, List.class);
        return (List<T>) list;
    }
}