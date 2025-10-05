package com.enterprise.documentsearch.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom metrics configuration for monitoring enterprise-scale
 * document search performance and system health.
 */
@Configuration
public class MetricsConfig {

    /**
     * Counter for document search operations
     */
    @Bean
    public Counter documentSearchCounter(MeterRegistry meterRegistry) {
        return Counter.builder("document.search.total")
                .description("Total number of document search operations")
                .tags("type", "search")
                .register(meterRegistry);
    }

    /**
     * Timer for document search latency
     */
    @Bean
    public Timer documentSearchTimer(MeterRegistry meterRegistry) {
        return Timer.builder("document.search.duration")
                .description("Document search operation duration")
                .tags("type", "search")
                .register(meterRegistry);
    }

    /**
     * Counter for document indexing operations
     */
    @Bean
    public Counter documentIndexCounter(MeterRegistry meterRegistry) {
        return Counter.builder("document.index.total")
                .description("Total number of document indexing operations")
                .tags("type", "index")
                .register(meterRegistry);
    }

    /**
     * Timer for document indexing duration
     */
    @Bean
    public Timer documentIndexTimer(MeterRegistry meterRegistry) {
        return Timer.builder("document.index.duration")
                .description("Document indexing operation duration")
                .tags("type", "index")
                .register(meterRegistry);
    }

    /**
     * Counter for file upload operations
     */
    @Bean
    public Counter fileUploadCounter(MeterRegistry meterRegistry) {
        return Counter.builder("file.upload.total")
                .description("Total number of file upload operations")
                .tags("type", "upload")
                .register(meterRegistry);
    }

    /**
     * Timer for file processing duration
     */
    @Bean
    public Timer fileProcessingTimer(MeterRegistry meterRegistry) {
        return Timer.builder("file.processing.duration")
                .description("File processing operation duration")
                .tags("type", "processing")
                .register(meterRegistry);
    }

    /**
     * Counter for cache hit operations
     */
    @Bean
    public Counter cacheHitCounter(MeterRegistry meterRegistry) {
        return Counter.builder("cache.hit.total")
                .description("Total number of cache hit operations")
                .tags("type", "hit")
                .register(meterRegistry);
    }

    /**
     * Counter for cache miss operations
     */
    @Bean
    public Counter cacheMissCounter(MeterRegistry meterRegistry) {
        return Counter.builder("cache.miss.total")
                .description("Total number of cache miss operations")
                .tags("type", "miss")
                .register(meterRegistry);
    }

    /**
     * Counter for rate limit violations
     */
    @Bean
    public Counter rateLimitCounter(MeterRegistry meterRegistry) {
        return Counter.builder("rate.limit.violations.total")
                .description("Total number of rate limit violations")
                .tags("type", "violation")
                .register(meterRegistry);
    }

    /**
     * Counter for circuit breaker events
     */
    @Bean
    public Counter circuitBreakerCounter(MeterRegistry meterRegistry) {
        return Counter.builder("circuit.breaker.events.total")
                .description("Total number of circuit breaker events")
                .tags("type", "circuit_breaker")
                .register(meterRegistry);
    }

    /**
     * Timer for database operations
     */
    @Bean
    public Timer databaseTimer(MeterRegistry meterRegistry) {
        return Timer.builder("database.operation.duration")
                .description("Database operation duration")
                .tags("type", "database")
                .register(meterRegistry);
    }

    /**
     * Timer for Elasticsearch operations
     */
    @Bean
    public Timer elasticsearchTimer(MeterRegistry meterRegistry) {
        return Timer.builder("elasticsearch.operation.duration")
                .description("Elasticsearch operation duration")
                .tags("type", "elasticsearch")
                .register(meterRegistry);
    }
}