package com.enterprise.documentsearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Caching configuration for enterprise-scale performance.
 * Implements multi-level caching with Redis for distributed caching
 * and Caffeine for local L1 cache.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${app.cache.documents.ttl:3600}")
    private long documentCacheTtlSeconds;

    @Value("${app.cache.search-results.ttl:1800}")
    private long searchResultsCacheTtlSeconds;

    @Value("${app.cache.metadata.ttl:7200}")
    private long metadataCacheTtlSeconds;

    @Value("${app.cache.user-preferences.ttl:86400}")
    private long userPreferencesCacheTtlSeconds;

    /**
     * Redis-based distributed cache manager for production environments
     */
    @Bean
    @Primary
    @Profile({"production", "docker", "development"})
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(documentCacheTtlSeconds))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // Configure specific cache TTLs for different data types
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Document content cache - frequently accessed, medium TTL
        cacheConfigurations.put("documents", defaultConfig
                .entryTtl(Duration.ofSeconds(documentCacheTtlSeconds)));
        
        // Search results cache - short TTL due to dynamic nature
        cacheConfigurations.put("searchResults", defaultConfig
                .entryTtl(Duration.ofSeconds(searchResultsCacheTtlSeconds)));
        
        // Document metadata cache - longer TTL, less frequently changed
        cacheConfigurations.put("documentMetadata", defaultConfig
                .entryTtl(Duration.ofSeconds(metadataCacheTtlSeconds)));
        
        // User preferences cache - long TTL, rarely changed
        cacheConfigurations.put("userPreferences", defaultConfig
                .entryTtl(Duration.ofSeconds(userPreferencesCacheTtlSeconds)));
        
        // Tenant configuration cache - very long TTL
        cacheConfigurations.put("tenantConfig", defaultConfig
                .entryTtl(Duration.ofHours(24)));
        
        // File content cache - medium TTL for processed file content
        cacheConfigurations.put("fileContent", defaultConfig
                .entryTtl(Duration.ofSeconds(documentCacheTtlSeconds)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    /**
     * Simple cache manager for test environments (in-memory)
     */
    @Bean
    @Profile("test")
    public CacheManager testCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Arrays.asList("documents", "searchResults", "documentMetadata", 
                                  "userPreferences", "tenantConfig", "fileContent"));
        return cacheManager;
    }
}