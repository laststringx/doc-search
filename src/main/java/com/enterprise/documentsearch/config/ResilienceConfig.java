package com.enterprise.documentsearch.config;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import io.github.bucket4j.Bandwidth;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for rate limiting and circuit breaker patterns
 * to handle enterprise-scale load and provide resilience.
 */
@Configuration
public class ResilienceConfig {

    private final ConcurrentHashMap<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    /**
     * Rate limiter configuration for search operations
     */
    @Bean
    public RateLimiterConfig searchRateLimiterConfig() {
        return RateLimiterConfig.custom()
                .limitForPeriod(1000) // 1000 requests per period
                .limitRefreshPeriod(Duration.ofMinutes(1)) // Refresh every minute
                .timeoutDuration(Duration.ofSeconds(5)) // Wait up to 5 seconds for permit
                .build();
    }

    /**
     * Rate limiter configuration for file upload operations
     */
    @Bean
    public RateLimiterConfig fileUploadRateLimiterConfig() {
        return RateLimiterConfig.custom()
                .limitForPeriod(100) // 100 uploads per period
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Search operations rate limiter
     */
    @Bean
    public RateLimiter searchRateLimiter() {
        return RateLimiter.of("searchRateLimiter", searchRateLimiterConfig());
    }

    /**
     * File upload operations rate limiter
     */
    @Bean
    public RateLimiter fileUploadRateLimiter() {
        return RateLimiter.of("fileUploadRateLimiter", fileUploadRateLimiterConfig());
    }

    /**
     * Circuit breaker configuration for Elasticsearch operations
     */
    @Bean
    public CircuitBreakerConfig elasticsearchCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // 50% failure rate threshold
                .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30 seconds before trying again
                .slidingWindowSize(10) // Consider last 10 calls
                .minimumNumberOfCalls(5) // At least 5 calls before calculating failure rate
                .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 calls in half-open state
                .slowCallRateThreshold(50) // 50% slow call rate threshold
                .slowCallDurationThreshold(Duration.ofSeconds(2)) // Calls slower than 2 seconds are slow
                .build();
    }

    /**
     * Circuit breaker configuration for database operations
     */
    @Bean
    public CircuitBreakerConfig databaseCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(60) // 60% failure rate threshold (more lenient for DB)
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .permittedNumberOfCallsInHalfOpenState(5)
                .slowCallRateThreshold(40)
                .slowCallDurationThreshold(Duration.ofSeconds(5)) // DB calls can be slower
                .build();
    }

    /**
     * Elasticsearch operations circuit breaker
     */
    @Bean
    public CircuitBreaker elasticsearchCircuitBreaker() {
        return CircuitBreaker.of("elasticsearchCircuitBreaker", elasticsearchCircuitBreakerConfig());
    }

    /**
     * Database operations circuit breaker
     */
    @Bean
    public CircuitBreaker databaseCircuitBreaker() {
        return CircuitBreaker.of("databaseCircuitBreaker", databaseCircuitBreakerConfig());
    }

    /**
     * Get or create a rate limiting bucket for a specific tenant
     * This provides per-tenant rate limiting capabilities
     */
    public Bucket getTenantBucket(String tenantId) {
        return bucketCache.computeIfAbsent(tenantId, this::createTenantBucket);
    }

    /**
     * Create a new bucket for tenant-specific rate limiting
     */
    private Bucket createTenantBucket(String tenantId) {
        // Different limits based on tenant type (could be configurable)
        Bandwidth limit = Bandwidth.classic(1000, Refill.intervally(1000, Duration.ofMinutes(1)));
        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Get or create a rate limiting bucket for API endpoints
     */
    public Bucket getApiBucket(String endpoint) {
        return bucketCache.computeIfAbsent("api:" + endpoint, this::createApiBucket);
    }

    /**
     * Create a new bucket for API endpoint rate limiting
     */
    private Bucket createApiBucket(String endpoint) {
        // More restrictive limits for certain endpoints
        Bandwidth limit;
        if (endpoint.contains("search")) {
            limit = Bandwidth.classic(500, Refill.intervally(500, Duration.ofMinutes(1)));
        } else if (endpoint.contains("upload")) {
            limit = Bandwidth.classic(50, Refill.intervally(50, Duration.ofMinutes(1)));
        } else {
            limit = Bandwidth.classic(200, Refill.intervally(200, Duration.ofMinutes(1)));
        }
        
        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }
}