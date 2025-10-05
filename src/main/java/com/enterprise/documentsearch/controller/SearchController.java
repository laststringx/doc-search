package com.enterprise.documentsearch.controller;

import com.enterprise.documentsearch.model.DocumentSearchEntity;
import com.enterprise.documentsearch.service.ElasticsearchService;
import com.enterprise.documentsearch.service.MetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for document search operations using Elasticsearch.
 * Provides high-performance full-text search capabilities with enterprise-scale features.
 * Disabled for test profile when Elasticsearch is not available.
 */
@RestController
@Profile("!test")
@ConditionalOnBean(ElasticsearchService.class)
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Document Search", description = "Elasticsearch-powered document search operations")
public class SearchController {

    private final ElasticsearchService elasticsearchService;
    private final MetricsService metricsService;

    /**
     * Full-text search across documents
     */
    @GetMapping
    @Operation(summary = "Search documents", description = "Perform full-text search across document titles, content, and tags")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @ApiResponse(responseCode = "503", description = "Search service unavailable")
    })
    @RateLimiter(name = "searchRateLimiter")
    @CircuitBreaker(name = "elasticsearchCircuitBreaker", fallbackMethod = "searchFallback")
    public ResponseEntity<Page<DocumentSearchEntity>> searchDocuments(
            @Parameter(description = "Tenant ID", required = true)
            @RequestHeader("X-Tenant-ID") String tenantId,
            
            @Parameter(description = "Search query", required = true)
            @RequestParam String query,
            
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Sort field", example = "createdDate")
            @RequestParam(defaultValue = "createdDate") String sortBy,
            
            @Parameter(description = "Sort direction", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDir) {

        log.info("Search request - tenant: {}, query: '{}', page: {}, size: {}", 
                tenantId, query, page, size);

        // Validate parameters
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        if (size > 100) { // Limit page size for performance
            size = 100;
        }

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<DocumentSearchEntity> results = elasticsearchService.searchDocuments(tenantId, query, pageable);
        
        log.debug("Search completed - tenant: {}, query: '{}', found: {} documents", 
                tenantId, query, results.getTotalElements());

        return ResponseEntity.ok(results);
    }

    /**
     * Advanced search with filters
     */
    @GetMapping("/advanced")
    @Operation(summary = "Advanced search", description = "Search with additional filters like file type and date range")
    @RateLimiter(name = "searchRateLimiter")
    @CircuitBreaker(name = "elasticsearchCircuitBreaker", fallbackMethod = "advancedSearchFallback")
    public ResponseEntity<Page<DocumentSearchEntity>> advancedSearch(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String query,
            @RequestParam(required = false) List<String> fileTypes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        log.info("Advanced search request - tenant: {}, query: '{}', types: {}, dateRange: {} to {}", 
                tenantId, query, fileTypes, startDate, endDate);

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        if (size > 100) {
            size = 100;
        }

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<DocumentSearchEntity> results = elasticsearchService.advancedSearch(
                tenantId, query, fileTypes, startDate, endDate, pageable);

        return ResponseEntity.ok(results);
    }

    /**
     * Get search suggestions for autocomplete
     */
    @GetMapping("/suggestions")
    @Operation(summary = "Get search suggestions", description = "Get title suggestions for autocomplete functionality")
    @RateLimiter(name = "searchRateLimiter")
    @CircuitBreaker(name = "elasticsearchCircuitBreaker", fallbackMethod = "suggestionsFallback")
    public ResponseEntity<List<DocumentSearchEntity>> getSuggestions(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String prefix) {

        if (prefix == null || prefix.trim().length() < 2) {
            return ResponseEntity.badRequest().build();
        }

        List<DocumentSearchEntity> suggestions = elasticsearchService.getTitleSuggestions(tenantId, prefix);
        return ResponseEntity.ok(suggestions);
    }

    /**
     * Get search statistics
     */
    @GetMapping("/stats")
    @Operation(summary = "Get search statistics", description = "Get document count and search statistics for tenant")
    public ResponseEntity<SearchStats> getSearchStats(@RequestHeader("X-Tenant-ID") String tenantId) {
        long documentCount = elasticsearchService.getDocumentCount(tenantId);
        
        SearchStats stats = SearchStats.builder()
                .tenantId(tenantId)
                .totalDocuments(documentCount)
                .build();
        
        return ResponseEntity.ok(stats);
    }

    /**
     * Fallback method for search operations when Elasticsearch is unavailable
     */
    public ResponseEntity<Page<DocumentSearchEntity>> searchFallback(String tenantId, String query, 
                                                                   int page, int size, String sortBy, String sortDir, Exception ex) {
        log.warn("Search fallback triggered for tenant: {} due to: {}", tenantId, ex.getMessage());
        metricsService.recordCircuitBreakerEvent("elasticsearch", "search_fallback");
        return ResponseEntity.status(503).build();
    }

    /**
     * Fallback method for advanced search
     */
    public ResponseEntity<Page<DocumentSearchEntity>> advancedSearchFallback(String tenantId, String query, 
                                                                            List<String> fileTypes, LocalDateTime startDate, LocalDateTime endDate,
                                                                            int page, int size, String sortBy, String sortDir, Exception ex) {
        log.warn("Advanced search fallback triggered for tenant: {} due to: {}", tenantId, ex.getMessage());
        metricsService.recordCircuitBreakerEvent("elasticsearch", "advanced_search_fallback");
        return ResponseEntity.status(503).build();
    }

    /**
     * Fallback method for suggestions
     */
    public ResponseEntity<List<DocumentSearchEntity>> suggestionsFallback(String tenantId, String prefix, Exception ex) {
        log.warn("Suggestions fallback triggered for tenant: {} due to: {}", tenantId, ex.getMessage());
        metricsService.recordCircuitBreakerEvent("elasticsearch", "suggestions_fallback");
        return ResponseEntity.ok(List.of());
    }

    /**
     * Search statistics data transfer object
     */
    @lombok.Data
    @lombok.Builder
    public static class SearchStats {
        private String tenantId;
        private long totalDocuments;
    }
}