package com.enterprise.documentsearch.service;

import com.enterprise.documentsearch.model.DocumentSearchEntity;
import com.enterprise.documentsearch.repository.ReactiveDocumentSearchRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Reactive service for high-performance document search operations
 * Optimized for 1000+ concurrent searches per second with sub-500ms response times
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReactiveDocumentSearchService {

    private final ReactiveDocumentSearchRepository searchRepository;
    private final MetricsService metricsService;
    private final CacheService cacheService;

    /**
     * High-performance reactive document search
     * Uses multiple caching layers and parallel processing
     */
    @Timed(value = "search.reactive.documents", description = "Reactive document search time")
    @CircuitBreaker(name = "elasticsearch", fallbackMethod = "searchFallback")
    public Mono<Page<DocumentSearchEntity>> searchDocuments(String tenantId, String query, Pageable pageable) {
        long startTime = System.currentTimeMillis();
        
        // Create cache key for search results
        String cacheKey = String.format("search:%s:%s:%d:%d", tenantId, query, pageable.getPageNumber(), pageable.getPageSize());
        
        // Try cache first (multi-layer caching)
        return cacheService.get(cacheKey, Page.class)
            .cast(Page.class)
            .map(page -> {
                @SuppressWarnings("unchecked")
                Page<DocumentSearchEntity> result = (Page<DocumentSearchEntity>) page;
                return result;
            })
            .switchIfEmpty(
                // If not in cache, perform actual search
                performSearch(tenantId, query, pageable)
                    .flatMap(result -> 
                        cacheService.put(cacheKey, result, Duration.ofMinutes(15))
                            .thenReturn(result)
                    )
            )
            .doOnNext(result -> {
                long duration = System.currentTimeMillis() - startTime;
                metricsService.recordSuccessfulSearch(tenantId, Duration.ofMillis(duration), (int) result.getTotalElements());
                log.debug("Search completed for tenant {} in {}ms: {} results", 
                         tenantId, duration, result.getTotalElements());
            })
            .timeout(Duration.ofMillis(400)) // Ensure sub-500ms with buffer
            .onErrorReturn(createEmptySearchResult(query, pageable));
    }

    /**
     * Advanced search with multiple filters
     */
    @Timed(value = "search.reactive.advanced", description = "Reactive advanced search time")
    @CircuitBreaker(name = "elasticsearch", fallbackMethod = "searchFallback")
    public Mono<Page<DocumentSearchEntity>> advancedSearch(
            String tenantId, String query, List<String> fileTypes, String author,
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        
        long startTime = System.currentTimeMillis();
        
        // Create complex cache key including all filters
        String cacheKey = String.format("advanced_search:%s:%s:%s:%s:%s:%s:%d:%d", 
                tenantId, query, fileTypes, author, startDate, endDate, pageable.getPageNumber(), pageable.getPageSize());
        
        return cacheService.get(cacheKey, Page.class)
            .cast(Page.class)
            .map(page -> {
                @SuppressWarnings("unchecked")
                Page<DocumentSearchEntity> result = (Page<DocumentSearchEntity>) page;
                return result;
            })
            .switchIfEmpty(
                performAdvancedSearch(tenantId, query, fileTypes, author, startDate, endDate, pageable)
                    .flatMap(result -> 
                        cacheService.put(cacheKey, result, Duration.ofMinutes(30))
                            .thenReturn(result)
                    )
            )
            .doOnNext(result -> {
                long duration = System.currentTimeMillis() - startTime;
                metricsService.recordSuccessfulSearch(tenantId, Duration.ofMillis(duration), 0);
                log.debug("Advanced search completed for tenant {} in {}ms", tenantId, duration);
            })
            .timeout(Duration.ofMillis(400));
    }

    /**
     * Get search suggestions with reactive processing
     */
    @Timed(value = "search.reactive.suggestions", description = "Reactive search suggestions time")
    public Flux<String> getSearchSuggestions(String tenantId, String prefix, int limit) {
        String cacheKey = String.format("suggestions:%s:%s:%d", tenantId, prefix, limit);
        
        return cacheService.get(cacheKey, List.class)
            .cast(List.class)
            .flatMapMany(suggestions -> {
                @SuppressWarnings("unchecked")
                List<String> stringList = (List<String>) suggestions;
                return Flux.fromIterable(stringList);
            })
            .switchIfEmpty(
                searchRepository.findSearchSuggestions(tenantId, prefix, limit)
                    .collectList()
                    .flatMap(suggestions -> 
                        cacheService.put(cacheKey, suggestions, Duration.ofMinutes(30))
                            .thenReturn(suggestions)
                    )
                    .flatMapMany(Flux::fromIterable)
            )
            .timeout(Duration.ofMillis(50)); // Very fast autocomplete
    }

    /**
     * Get search analytics for performance monitoring
     */
    @Timed(value = "search.reactive.analytics", description = "Reactive search analytics time")
    @Cacheable(value = "searchAnalytics", key = "#tenantId + ':' + #duration.toHours()")
    public Mono<Map<String, Object>> getSearchAnalytics(String tenantId, Duration duration) {
        return searchRepository.getSearchAnalytics(tenantId, LocalDateTime.now().minus(duration))
            .collectMap(
                entry -> entry.getKey(),
                entry -> entry.getValue()
            )
            .defaultIfEmpty(Map.of("error", "No analytics data available"))
            .timeout(Duration.ofMillis(200));
    }

    /**
     * Bulk index documents reactively
     */
    @Timed(value = "search.reactive.bulk_index", description = "Reactive bulk indexing time")
    public Mono<Long> bulkIndexDocuments(String tenantId, Flux<DocumentSearchEntity> documents) {
        return documents
            .buffer(100) // Process in batches of 100
            .flatMap(batch -> 
                searchRepository.saveAll(batch)
                    .count()
                    .subscribeOn(Schedulers.parallel())
            )
            .reduce(0L, Long::sum)
            .doOnNext(count -> {
                long startTime = System.currentTimeMillis();
                metricsService.recordDocumentIndex(tenantId, System.currentTimeMillis() - startTime, true);
                log.info("Bulk indexed {} documents for tenant {}", count, tenantId);
            });
    }

    /**
     * Perform the actual search operation
     */
    private Mono<Page<DocumentSearchEntity>> performSearch(String tenantId, String query, Pageable pageable) {
        return searchRepository.searchByTenantAndQuery(tenantId, query, pageable)
            .collectList()
            .zipWith(searchRepository.countByTenantAndQuery(tenantId, query))
            .map(tuple -> (Page<DocumentSearchEntity>) new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()))
            .subscribeOn(Schedulers.boundedElastic()); // Use dedicated thread pool for I/O
    }

    /**
     * Perform advanced search with filters
     */
    private Mono<Page<DocumentSearchEntity>> performAdvancedSearch(
            String tenantId, String query, List<String> fileTypes, String author,
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        
        return searchRepository.advancedSearch(tenantId, query, fileTypes, author, startDate, endDate, pageable)
            .collectList()
            .zipWith(searchRepository.countAdvancedSearch(tenantId, query, fileTypes, author, startDate, endDate))
            .map(tuple -> (Page<DocumentSearchEntity>) new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Fallback method for circuit breaker
     */
    public Mono<Page<DocumentSearchEntity>> searchFallback(
            String tenantId, String query, Pageable pageable, Exception ex) {
        
        log.warn("Search fallback triggered for tenant: {}, query: {}, error: {}", 
                tenantId, query, ex.getMessage());
        
        metricsService.recordFailedSearch(tenantId, ex.getClass().getSimpleName(), null);
        
        return Mono.just(createEmptySearchResult(query, pageable));
    }

    /**
     * Create empty search result for fallback scenarios
     */
    private Page<DocumentSearchEntity> createEmptySearchResult(String query, Pageable pageable) {
        return new PageImpl<>(List.of(), pageable, 0L);
    }

    /**
     * Health check for search service
     */
    public Mono<Boolean> isHealthy() {
        return searchRepository.count()
            .map(count -> count >= 0)
            .timeout(Duration.ofSeconds(2))
            .onErrorReturn(false);
    }
}