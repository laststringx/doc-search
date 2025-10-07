package com.enterprise.documentsearch.controller;

import com.enterprise.documentsearch.dto.DocumentResponse;
import com.enterprise.documentsearch.dto.SearchResponse;
import com.enterprise.documentsearch.model.DocumentSearchEntity;
import com.enterprise.documentsearch.service.DocumentSearchService;
import com.enterprise.documentsearch.service.RateLimitingService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST controller for high-performance document search operations
 * Handles search requests with traditional Spring MVC architecture
 */
@RestController
@RequestMapping("/api/v1/reactive-search")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class ReactiveSearchController {

    private final DocumentSearchService searchService;
    private final RateLimitingService rateLimitingService;

    /**
     * High-performance document search endpoint
     * Traditional synchronous implementation
     */
    @GetMapping
    @Timed(value = "search.documents", description = "Time taken for document search")
    @CircuitBreaker(name = "document-search", fallbackMethod = "searchFallback") 
    public ResponseEntity<SearchResponse> searchDocuments(
            @RequestParam String q,
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
            HttpServletRequest request) {

        try {
            // Use tenant ID from header if not provided in query
            String effectiveTenantId = tenantId != null ? tenantId : headerTenantId;
            
            if (effectiveTenantId == null) {
                return ResponseEntity.badRequest()
                    .body(SearchResponse.builder()
                        .query(q)
                        .totalElements(0L)
                        .totalPages(0)
                        .currentPage(page)
                        .pageSize(size)
                        .error("Tenant ID is required")
                        .build());
            }

            // Rate limiting check
            Boolean allowed = rateLimitingService.isAllowed(effectiveTenantId, "user").block();
            if (Boolean.FALSE.equals(allowed)) {
                throw new RuntimeException("Rate limit exceeded for tenant: " + effectiveTenantId);
            }

            long startTime = System.currentTimeMillis();
            Pageable pageable = PageRequest.of(page, Math.min(size, 100)); // Limit max size
            
            Page<DocumentSearchEntity> searchResults = searchService.searchDocuments(effectiveTenantId, q, pageable);
            long duration = System.currentTimeMillis() - startTime;
            
            log.debug("Search completed for tenant {} in {}ms, found {} results", 
                    effectiveTenantId, duration, searchResults.getTotalElements());

            SearchResponse response = SearchResponse.fromPage(searchResults, q, duration);
            
            return ResponseEntity.ok()
                .header("X-Response-Time", String.valueOf(duration))
                .header("X-Tenant-ID", effectiveTenantId)
                .body(response);

        } catch (Exception e) {
            log.error("Search failed for query: '{}', error: {}", q, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SearchResponse.builder()
                    .query(q)
                    .totalElements(0L)
                    .totalPages(0)
                    .currentPage(page)
                    .pageSize(size)
                    .hasNext(false)
                    .hasPrevious(false)
                    .searchTime(0L)
                    .documents(List.of())
                    .error("Search service temporarily unavailable")
                    .build());
        }
    }

    /**
     * Advanced search with filters for complex queries
     */
    @GetMapping("/advanced")
    @Timed(value = "search.advanced", description = "Time taken for advanced search")
    @CircuitBreaker(name = "document-search", fallbackMethod = "searchFallback") 
    public ResponseEntity<SearchResponse> advancedSearch(
            @RequestParam String q,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
            HttpServletRequest request) {

        try {
            String effectiveTenantId = tenantId != null ? tenantId : headerTenantId;
            
            if (effectiveTenantId == null) {
                return ResponseEntity.badRequest()
                    .body(SearchResponse.builder()
                        .query(q)
                        .error("Tenant ID is required")
                        .build());
            }

            Boolean allowed = rateLimitingService.isAllowed(effectiveTenantId, "user").block();
            if (Boolean.FALSE.equals(allowed)) {
                throw new RuntimeException("Rate limit exceeded");
            }

            long startTime = System.currentTimeMillis();
            Pageable pageable = PageRequest.of(page, Math.min(size, 100));
            
            Page<DocumentSearchEntity> result = searchService.advancedSearch(effectiveTenantId, q, fileType, author, 
                    dateFrom, dateTo, pageable);
            long duration = System.currentTimeMillis() - startTime;
            
            SearchResponse response = SearchResponse.fromPage(result, q, duration);
            return ResponseEntity.ok()
                .header("X-Response-Time", String.valueOf(duration))
                .body(response);

        } catch (Exception e) {
            log.error("Advanced search failed for query: '{}', error: {}", q, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SearchResponse.builder()
                    .query(q)
                    .error("Advanced search service temporarily unavailable")
                    .build());
        }
    }

    /**
     * Autocomplete suggestions for search queries
     */
    @GetMapping("/suggestions")
    @Timed(value = "search.suggestions", description = "Time taken for search suggestions")
    public ResponseEntity<List<String>> getSearchSuggestions(
            @RequestParam String prefix,
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId) {

        try {
            String effectiveTenantId = tenantId != null ? tenantId : headerTenantId;
            
            if (effectiveTenantId == null) {
                return ResponseEntity.badRequest().build();
            }

            List<String> suggestions = searchService.getSearchSuggestions(effectiveTenantId, prefix, Math.min(limit, 20));
            
            return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=300") // Cache for 5 minutes
                .body(suggestions);
                
        } catch (Exception e) {
            log.error("Failed to get suggestions for prefix: '{}', error: {}", prefix, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get search analytics for a tenant
     */
    @GetMapping("/analytics")
    @Timed(value = "search.analytics", description = "Time taken for search analytics")
    public ResponseEntity<Map<String, Object>> getSearchAnalytics(
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "24") int hours,
            @RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId) {

        try {
            String effectiveTenantId = tenantId != null ? tenantId : headerTenantId;
            
            if (effectiveTenantId == null) {
                return ResponseEntity.badRequest().build();
            }

            Map<String, Object> analytics = searchService.getSearchAnalytics(effectiveTenantId, Duration.ofHours(hours));
            
            return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=60") // Cache for 1 minute
                .body(analytics);
                
        } catch (Exception e) {
            log.error("Failed to get analytics for tenant: {}, error: {}", tenantId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Fallback method for circuit breaker
     */
    public ResponseEntity<SearchResponse> searchFallback(
            String q, String tenantId, int page, int size, String headerTenantId, 
            HttpServletRequest request, Exception ex) {
        
        log.warn("Search fallback triggered for query: {} in tenant: {}, error: {}", 
                q, tenantId != null ? tenantId : headerTenantId, ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(SearchResponse.builder()
                .query(q)
                .error("Search service temporarily unavailable. Please try again.")
                .currentPage(page)
                .pageSize(size)
                .build());
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