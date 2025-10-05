package com.enterprise.documentsearch.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Enterprise metrics service for monitoring and observability
 * Tracks performance, usage, and system health metrics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Timer> searchTimers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> searchCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> concurrentSearches = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> lastActivity = new ConcurrentHashMap<>();

    @Value("${app.search.monitoring.slow-query-threshold:1s}")
    private Duration slowQueryThreshold = Duration.ofSeconds(1);

    /**
     * Record a document search operation (legacy method for compatibility)
     */
    public void recordDocumentSearch(String tenantId, long durationMs, boolean successful) {
        Duration duration = Duration.ofMillis(durationMs);
        if (successful) {
            recordSuccessfulSearch(tenantId, duration, 0);
        } else {
            recordFailedSearch(tenantId, "unknown", duration);
        }
    }

    /**
     * Record search request metrics
     */
    public void recordSearchRequest(String tenantId, String operation) {
        getSearchCounter("total", "requests").increment();
        getSearchCounter(tenantId, "requests").increment();
        
        concurrentSearches.computeIfAbsent(tenantId, k -> new LongAdder()).increment();
        lastActivity.put(tenantId, new AtomicLong(System.currentTimeMillis()));
    }

    /**
     * Record successful search
     */
    public void recordSuccessfulSearch(String tenantId, Duration duration, int resultCount) {
        getSearchCounter("total", "successful").increment();
        getSearchCounter(tenantId, "successful").increment();
        getSearchTimer("total", "latency").record(duration);
        getSearchTimer(tenantId, "latency").record(duration);
        
        if (duration.compareTo(slowQueryThreshold) > 0) {
            getSearchCounter(tenantId, "slow_queries").increment();
        }

        LongAdder concurrent = concurrentSearches.get(tenantId);
        if (concurrent != null) {
            concurrent.decrement();
        }
    }

    /**
     * Record failed search
     */
    public void recordFailedSearch(String tenantId, String error, Duration duration) {
        getSearchCounter("total", "failed").increment();
        getSearchCounter(tenantId, "failed").increment();
        getSearchCounter(tenantId, "error:" + error).increment();
        
        if (duration != null) {
            getSearchTimer(tenantId, "failed_latency").record(duration);
        }

        LongAdder concurrent = concurrentSearches.get(tenantId);
        if (concurrent != null) {
            concurrent.decrement();
        }
    }

    /**
     * Record cache hit
     */
    public void recordCacheHit(String tenantId, String cacheType) {
        getSearchCounter("total", "cache_hits").increment();
        getSearchCounter(tenantId, "cache_hits:" + cacheType).increment();
    }

    /**
     * Record cache miss
     */
    public void recordCacheMiss(String tenantId, String cacheType) {
        getSearchCounter("total", "cache_misses").increment();
        getSearchCounter(tenantId, "cache_misses:" + cacheType).increment();
    }

    /**
     * Record rate limit exceeded
     */
    public void recordRateLimitExceeded(String tenantId, String limitType) {
        getSearchCounter("total", "rate_limit_exceeded").increment();
        getSearchCounter(tenantId, "rate_limit:" + limitType).increment();
    }

    /**
     * Record a document indexing operation (enhanced)
     */
    public void recordDocumentIndex(String tenantId, long durationMs, boolean successful) {
        recordDocumentIndexing(tenantId, Duration.ofMillis(durationMs), successful);
    }

    /**
     * Record document indexing
     */
    public void recordDocumentIndexing(String tenantId, Duration duration, boolean success) {
        Timer indexTimer = getSearchTimer("total", "indexing_latency");
                .tag("tenant", tenantId)
                .register(meterRegistry)
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        log.debug("Recorded document index: tenant={}, duration={}ms, successful={}", 
                tenantId, durationMs, successful);
    }

    /**
     * Record a file upload operation
     */
    public void recordFileUpload(String tenantId, String fileType, long fileSizeBytes, boolean successful) {
        Counter.builder("file.upload.total")
                .tag("tenant", tenantId)
                .tag("file_type", fileType)
                .tag("status", successful ? "success" : "failure")
                .register(meterRegistry)
                .increment();
        
        log.debug("Recorded file upload: tenant={}, type={}, size={}bytes, successful={}", 
                tenantId, fileType, fileSizeBytes, successful);
    }

    /**
     * Record file processing duration
     */
    public void recordFileProcessing(String tenantId, String fileType, long durationMs) {
        Timer.builder("file.processing.duration")
                .tag("tenant", tenantId)
                .tag("file_type", fileType)
                .register(meterRegistry)
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        log.debug("Recorded file processing: tenant={}, type={}, duration={}ms", 
                tenantId, fileType, durationMs);
    }

    /**
     * Record cache hit
     */
    public void recordCacheHit(String cacheType, String tenantId) {
        Counter.builder("cache.hit.total")
                .tag("cache_type", cacheType)
                .tag("tenant", tenantId)
                .register(meterRegistry)
                .increment();
        
        log.debug("Recorded cache hit: type={}, tenant={}", cacheType, tenantId);
    }

    /**
     * Record cache miss
     */
    public void recordCacheMiss(String cacheType, String tenantId) {
        Counter.builder("cache.miss.total")
                .tag("cache_type", cacheType)
                .tag("tenant", tenantId)
                .register(meterRegistry)
                .increment();
        
        log.debug("Recorded cache miss: type={}, tenant={}", cacheType, tenantId);
    }

    /**
     * Record rate limit violation
     */
    public void recordRateLimitViolation(String tenantId, String endpoint) {
        Counter.builder("rate.limit.violations.total")
                .tag("tenant", tenantId)
                .tag("endpoint", endpoint)
                .register(meterRegistry)
                .increment();
        
        log.warn("Recorded rate limit violation: tenant={}, endpoint={}", tenantId, endpoint);
    }

    /**
     * Record circuit breaker event
     */
    public void recordCircuitBreakerEvent(String service, String event) {
        Counter.builder("circuit.breaker.events.total")
                .tag("service", service)
                .tag("event", event)
                .register(meterRegistry)
                .increment();
        
        log.warn("Recorded circuit breaker event: service={}, event={}", service, event);
    }

    /**
     * Record database operation duration
     */
    public void recordDatabaseOperation(String operation, long durationMs, boolean successful) {
        Timer.builder("database.operation.duration")
                .tag("operation", operation)
                .tag("status", successful ? "success" : "failure")
                .register(meterRegistry)
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        log.debug("Recorded database operation: operation={}, duration={}ms, successful={}", 
                operation, durationMs, successful);
    }

    /**
     * Record Elasticsearch operation duration
     */
    public void recordElasticsearchOperation(String operation, long durationMs, boolean successful) {
        Timer.builder("elasticsearch.operation.duration")
                .tag("operation", operation)
                .tag("status", successful ? "success" : "failure")
                .register(meterRegistry)
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        log.debug("Recorded Elasticsearch operation: operation={}, duration={}ms, successful={}", 
                operation, durationMs, successful);
    }
}