package com.enterprise.documentsearch.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Multi-layer caching service for enterprise document search
 * L1: Caffeine in-memory cache for ultra-fast access
 * L2: Redis cluster for distributed caching
 */
@Service
public class CacheService {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
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
    public CacheService(ReactiveRedisTemplate<String, Object> redisTemplate) {
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
    public <T> Mono<T> get(String key, Class<T> type) {
        // Try L1 cache first
        T l1Value = (T) l1Cache.getIfPresent(key);
        if (l1Value != null) {
            return Mono.just(l1Value);
        }

        // Try L2 cache (Redis cluster)
        String redisKey = keyPrefix + key;
        return redisTemplate.opsForValue()
                .get(redisKey)
                .cast(type)
                .doOnNext(value -> {
                    // Populate L1 cache for future requests
                    if (value != null) {
                        l1Cache.put(key, value);
                    }
                });
    }

    /**
     * Put value into both cache layers
     */
    public <T> Mono<Void> put(String key, T value) {
        // Store in L1 cache
        l1Cache.put(key, value);

        // Store in L2 cache (Redis cluster)
        String redisKey = keyPrefix + key;
        return redisTemplate.opsForValue()
                .set(redisKey, value, l2ExpireAfterWrite)
                .then();
    }

    /**
     * Put value with custom TTL
     */
    public <T> Mono<Void> put(String key, T value, Duration ttl) {
        // Store in L1 cache
        l1Cache.put(key, value);

        // Store in L2 cache with custom TTL
        String redisKey = keyPrefix + key;
        return redisTemplate.opsForValue()
                .set(redisKey, value, ttl)
                .then();
    }

    /**
     * Get or compute value if not present
     */
    public <T> Mono<T> getOrCompute(String key, Class<T> type, Mono<T> computeFunction) {
        return get(key, type)
                .switchIfEmpty(
                        computeFunction
                                .flatMap(computed -> 
                                        put(key, computed)
                                                .thenReturn(computed)
                                )
                );
    }

    /**
     * Invalidate key from both cache layers
     */
    public Mono<Void> evict(String key) {
        // Remove from L1 cache
        l1Cache.invalidate(key);

        // Remove from L2 cache
        String redisKey = keyPrefix + key;
        return redisTemplate.delete(redisKey).then();
    }

    /**
     * Bulk eviction for multiple keys
     */
    public Mono<Void> evictAll(List<String> keys) {
        // Remove from L1 cache
        keys.forEach(l1Cache::invalidate);

        // Remove from L2 cache
        List<String> redisKeys = keys.stream()
                .map(key -> keyPrefix + key)
                .toList();

        return redisTemplate.delete(redisKeys.toArray(new String[0])).then();
    }

    /**
     * Clear all cache entries for a tenant
     */
    public Mono<Void> evictTenant(String tenantId) {
        String pattern = keyPrefix + "tenant:" + tenantId + ":*";
        
        return redisTemplate.keys(pattern)
                .collectList()
                .flatMap(keys -> {
                    if (!keys.isEmpty()) {
                        // Remove from L1 cache
                        keys.forEach(redisKey -> {
                            String cacheKey = redisKey.substring(keyPrefix.length());
                            l1Cache.invalidate(cacheKey);
                        });

                        // Remove from L2 cache
                        return redisTemplate.delete(keys.toArray(new String[0])).then();
                    }
                    return Mono.empty();
                });
    }

    /**
     * Warm up cache with frequently accessed data
     */
    public Mono<Void> warmUpCache(String tenantId, List<String> searchTerms) {
        return Flux.fromIterable(searchTerms)
                .flatMap(term -> {
                    String cacheKey = "search:" + tenantId + ":" + term;
                    // Pre-load common search results
                    return get(cacheKey, Object.class)
                            .switchIfEmpty(Mono.empty());
                })
                .then();
    }

    /**
     * Get cache statistics
     */
    public Mono<CacheStats> getStats() {
        var caffeinStats = l1Cache.stats();
        
        // Get Redis info - simplified version for compatibility
        return Mono.just("Redis cluster info")
                .map(info -> new CacheStats(
                        l1Cache.estimatedSize(),
                        caffeinStats.hitRate(),
                        caffeinStats.evictionCount(),
                        info
                ));
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
    public Mono<Boolean> exists(String key) {
        // Check L1 first
        if (l1Cache.getIfPresent(key) != null) {
            return Mono.just(true);
        }

        // Check L2
        String redisKey = keyPrefix + key;
        return redisTemplate.hasKey(redisKey);
    }

    /**
     * Set expiration for existing key
     */
    public Mono<Void> expire(String key, Duration ttl) {
        String redisKey = keyPrefix + key;
        return redisTemplate.expire(redisKey, ttl).then();
    }

    /**
     * Get remaining TTL for key
     */
    public Mono<Duration> getTtl(String key) {
        String redisKey = keyPrefix + key;
        return redisTemplate.getExpire(redisKey);
    }

    /**
     * Increment counter in cache
     */
    public Mono<Long> increment(String key) {
        String redisKey = keyPrefix + key;
        return redisTemplate.opsForValue().increment(redisKey);
    }

    /**
     * Increment counter with expiration
     */
    public Mono<Long> increment(String key, Duration expiration) {
        String redisKey = keyPrefix + key;
        return redisTemplate.opsForValue()
                .increment(redisKey)
                .flatMap(count -> {
                    if (count == 1) {
                        // Set expiration only for new keys
                        return redisTemplate.expire(redisKey, expiration)
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                });
    }

    /**
     * Store search results with optimized serialization
     */
    public <T> Mono<Void> putSearchResults(String tenantId, String query, List<T> results, int page) {
        String cacheKey = "search:" + tenantId + ":" + query + ":page:" + page;
        return put(cacheKey, results);
    }

    /**
     * Get cached search results
     */
    @SuppressWarnings("unchecked")
    public <T> Mono<List<T>> getSearchResults(String tenantId, String query, int page, Class<T> type) {
        String cacheKey = "search:" + tenantId + ":" + query + ":page:" + page;
        return get(cacheKey, List.class)
                .map(list -> (List<T>) list);
    }
}