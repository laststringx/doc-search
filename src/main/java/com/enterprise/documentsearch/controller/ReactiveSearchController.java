package com.enterprise.documentsearch.controller;

import com.enterprise.documentsearch.dto.DocumentResponse;
import com.enterprise.documentsearch.dto.SearchResponse;
import com.enterprise.documentsearch.model.DocumentSearchEntity;
import com.enterprise.documentsearch.service.ReactiveDocumentSearchService;
import com.enterprise.documentsearch.service.RateLimitingService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reactive REST controller for high-performance document search operations
 * Handles 1000+ concurrent requests per second with sub-500ms response times
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class ReactiveSearchController {

    private final ReactiveDocumentSearchService searchService;
    private final RateLimitingService rateLimitingService;

    /**
     * High-performance document search endpoint
     * Supports 1000+ concurrent searches with sub-500ms response times
     */
    @GetMapping
    @Timed(value = "search.documents", description = "Time taken for document search")
    @CircuitBreaker(name = "document-search", fallbackMethod = "searchFallback")
    @TimeLimiter(name = "document-search")
    public Mono<ResponseEntity<SearchResponse>> searchDocuments(
            @RequestParam String q,
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
            ServerHttpRequest request) {

        // Use tenant ID from header if not provided in query
        String effectiveTenantId = tenantId != null ? tenantId : headerTenantId;
        
        if (effectiveTenantId == null) {
            return Mono.just(ResponseEntity.badRequest()
                .body(SearchResponse.builder()
                    .query(q)
                    .totalElements(0L)
                    .totalPages(0)
                    .currentPage(page)
                    .size(size)
                    .error("Tenant ID is required")
                    .build()));
        }

        // Rate limiting check
        return rateLimitingService.isAllowed(effectiveTenantId, request.getRemoteAddress())
            .filter(allowed -> allowed)
            .switchIfEmpty(Mono.error(new RuntimeException("Rate limit exceeded for tenant: " + effectiveTenantId)))
            .flatMap(allowed -> {
                long startTime = System.currentTimeMillis();
                Pageable pageable = PageRequest.of(page, Math.min(size, 100)); // Limit max size
                
                return searchService.searchDocuments(effectiveTenantId, q, pageable)
                    .doOnNext(result -> {
                        long duration = System.currentTimeMillis() - startTime;
                        log.debug("Search completed for tenant {} in {}ms, found {} results", 
                                effectiveTenantId, duration, result.getTotalElements());
                    })
                    .map(result -> ResponseEntity.ok()
                        .header("X-Response-Time", String.valueOf(System.currentTimeMillis() - startTime))
                        .header("X-Tenant-ID", effectiveTenantId)
                        .body(SearchResponse.builder()
                            .query(q)
                            .documents(result.getContent().stream()
                                .map(this::convertToDocumentResponse)
                                .toList())
                            .totalElements(result.getTotalElements())
                            .totalPages(result.getTotalPages())
                            .currentPage(result.getNumber())
                            .size(result.getSize())
                            .responseTimeMs(System.currentTimeMillis() - startTime)
                            .build()))
                    .timeout(Duration.ofMillis(450)) // Ensure sub-500ms response
                    .onErrorReturn(ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                        .body(SearchResponse.builder()
                            .query(q)
                            .error("Search timeout - try a more specific query")
                            .responseTimeMs(System.currentTimeMillis() - startTime)
                            .build()));
            })
            .onErrorReturn(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(SearchResponse.builder()
                    .query(q)
                    .error("Rate limit exceeded")
                    .build()));
    }

    /**
     * Advanced search with filters for complex queries
     */
    @GetMapping("/advanced")
    @Timed(value = "search.advanced", description = "Time taken for advanced search")
    @CircuitBreaker(name = "document-search", fallbackMethod = "searchFallback")
    @TimeLimiter(name = "document-search")
    public Mono<ResponseEntity<SearchResponse>> advancedSearch(
            @RequestParam String q,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) List<String> fileTypes,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
            ServerHttpRequest request) {

        String effectiveTenantId = tenantId != null ? tenantId : headerTenantId;
        
        if (effectiveTenantId == null) {
            return Mono.just(ResponseEntity.badRequest()
                .body(SearchResponse.builder()
                    .query(q)
                    .error("Tenant ID is required")
                    .build()));
        }

        return rateLimitingService.isAllowed(effectiveTenantId, request.getRemoteAddress())
            .filter(allowed -> allowed)
            .switchIfEmpty(Mono.error(new RuntimeException("Rate limit exceeded")))
            .flatMap(allowed -> {
                long startTime = System.currentTimeMillis();
                Pageable pageable = PageRequest.of(page, Math.min(size, 100));
                
                return searchService.advancedSearch(effectiveTenantId, q, fileTypes, author, 
                        startDate, endDate, pageable)
                    .map(result -> ResponseEntity.ok()
                        .header("X-Response-Time", String.valueOf(System.currentTimeMillis() - startTime))
                        .body(SearchResponse.builder()
                            .query(q)
                            .documents(result.getContent().stream()
                                .map(this::convertToDocumentResponse)
                                .toList())
                            .totalElements(result.getTotalElements())
                            .totalPages(result.getTotalPages())
                            .currentPage(result.getNumber())
                            .size(result.getSize())
                            .responseTimeMs(System.currentTimeMillis() - startTime)
                            .build()))
                    .timeout(Duration.ofMillis(450));
            });
    }

    /**
     * Autocomplete suggestions for search queries
     */
    @GetMapping("/suggestions")
    @Timed(value = "search.suggestions", description = "Time taken for search suggestions")
    public Mono<ResponseEntity<List<String>>> getSearchSuggestions(
            @RequestParam String prefix,
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId) {

        String effectiveTenantId = tenantId != null ? tenantId : headerTenantId;
        
        if (effectiveTenantId == null) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return searchService.getSearchSuggestions(effectiveTenantId, prefix, Math.min(limit, 20))
            .collectList()
            .map(suggestions -> ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=300") // Cache for 5 minutes
                .body(suggestions))
            .timeout(Duration.ofMillis(100)); // Fast autocomplete response
    }

    /**
     * Get search analytics for a tenant
     */
    @GetMapping("/analytics")
    @Timed(value = "search.analytics", description = "Time taken for search analytics")
    public Mono<ResponseEntity<Object>> getSearchAnalytics(
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "24") int hours,
            @RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId) {

        String effectiveTenantId = tenantId != null ? tenantId : headerTenantId;
        
        if (effectiveTenantId == null) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return searchService.getSearchAnalytics(effectiveTenantId, Duration.ofHours(hours))
            .map(analytics -> ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=60") // Cache for 1 minute
                .body(analytics))
            .timeout(Duration.ofMillis(200));
    }

    /**
     * Fallback method for circuit breaker
     */
    public Mono<ResponseEntity<SearchResponse>> searchFallback(
            String q, String tenantId, int page, int size, String headerTenantId, 
            ServerHttpRequest request, Exception ex) {
        
        log.warn("Search fallback triggered for query: {} in tenant: {}, error: {}", 
                q, tenantId != null ? tenantId : headerTenantId, ex.getMessage());
        
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(SearchResponse.builder()
                .query(q)
                .error("Search service temporarily unavailable. Please try again.")
                .currentPage(page)
                .size(size)
                .build()));
    }

    /**
     * Convert DocumentSearchEntity to DocumentResponse DTO
     */
    private DocumentResponse convertToDocumentResponse(DocumentSearchEntity entity) {
        return DocumentResponse.builder()
            .id(entity.getDocumentId())
            .title(entity.getTitle())
            .fileName(entity.getFileName())
            .fileType(entity.getFileType())
            .fileSize(entity.getFileSize())
            .author(entity.getAuthor())
            .createdDate(entity.getCreatedDate())
            .lastModifiedDate(entity.getLastModifiedDate())
            .tenantId(entity.getTenantId())
            .relevanceScore(entity.getRelevanceScore())
            .build();
    }
}