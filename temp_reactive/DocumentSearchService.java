package com.enterprise.documentsearch.service;

import com.enterprise.documentsearch.model.DocumentSearchEntity;
import com.enterprise.documentsearch.repository.DocumentSearchRepository;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Traditional service for document search operations
 * Handles search, indexing, and analytics functionality
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentSearchService {

    private final DocumentSearchRepository searchRepository;
    private final MetricsService metricsService;

    /**
     * Search documents with query and pagination
     */
    @Timed(value = "search.documents", description = "Document search time")
    @Cacheable(value = "searchResults", key = "#tenantId + ':' + #query + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<DocumentSearchEntity> searchDocuments(String tenantId, String query, Pageable pageable) {
        try {
            log.debug("Searching documents for tenant: {}, query: '{}'", tenantId, query);
            
            // Record search metrics
            metricsService.recordSearchRequest(tenantId, "search");
            
            long startTime = System.currentTimeMillis();
            Page<DocumentSearchEntity> results = searchRepository.searchByTenantAndQuery(tenantId, query, pageable);
            long duration = System.currentTimeMillis() - startTime;
            
            // Record search timing
            metricsService.recordSearchTime(duration);
            
            log.info("Search completed for tenant: {}, query: '{}', results: {}, time: {}ms", 
                    tenantId, query, results.getTotalElements(), duration);
            
            return results;
            
        } catch (Exception e) {
            log.error("Search failed for tenant: {}, query: '{}', error: {}", tenantId, query, e.getMessage(), e);
            metricsService.recordSearchError("search_error");
            throw new RuntimeException("Search operation failed", e);
        }
    }

    /**
     * Advanced search with multiple criteria
     */
    @Timed(value = "search.advanced", description = "Advanced search time")
    public Page<DocumentSearchEntity> advancedSearch(
            String tenantId, String query, String fileType, String author, 
            String dateFrom, String dateTo, Pageable pageable) {
        
        try {
            log.debug("Advanced search for tenant: {}, query: '{}', fileType: {}, author: {}", 
                    tenantId, query, fileType, author);
            
            metricsService.recordSearchRequest(tenantId);
            
            long startTime = System.currentTimeMillis();
            
            // Use repository method that supports multiple criteria
            Page<DocumentSearchEntity> results = searchRepository.findByAdvancedCriteria(
                tenantId, query, fileType, author, dateFrom, dateTo, pageable);
            
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordSearchTime(duration);
            
            log.info("Advanced search completed for tenant: {}, results: {}, time: {}ms", 
                    tenantId, results.getTotalElements(), duration);
            
            return results;
            
        } catch (Exception e) {
            log.error("Advanced search failed for tenant: {}, error: {}", tenantId, e.getMessage(), e);
            metricsService.recordSearchError(tenantId);
            throw new RuntimeException("Advanced search operation failed", e);
        }
    }

    /**
     * Get search suggestions for autocomplete
     */
    @Timed(value = "search.suggestions", description = "Search suggestions time")
    @Cacheable(value = "searchSuggestions", key = "#tenantId + ':' + #prefix")
    public List<String> getSearchSuggestions(String tenantId, String prefix, int limit) {
        try {
            log.debug("Getting search suggestions for tenant: {}, prefix: '{}'", tenantId, prefix);
            
            List<String> suggestions = searchRepository.findSuggestionsByPrefix(tenantId, prefix, limit);
            
            log.debug("Found {} suggestions for prefix: '{}'", suggestions.size(), prefix);
            return suggestions;
            
        } catch (Exception e) {
            log.error("Failed to get suggestions for tenant: {}, prefix: '{}', error: {}", 
                    tenantId, prefix, e.getMessage(), e);
            return List.of(); // Return empty list on error
        }
    }

    /**
     * Get search analytics for tenant
     */
    @Timed(value = "search.analytics", description = "Search analytics time")
    @Cacheable(value = "searchAnalytics", key = "#tenantId + ':' + #duration.toHours()")
    public Map<String, Object> getSearchAnalytics(String tenantId, Duration duration) {
        try {
            log.debug("Getting search analytics for tenant: {}, duration: {}", tenantId, duration);
            
            LocalDateTime since = LocalDateTime.now().minus(duration);
            
            Map<String, Object> analytics = new HashMap<>();
            analytics.put("tenantId", tenantId);
            analytics.put("period", duration.toString());
            analytics.put("since", since);
            
            // Get search count
            long searchCount = searchRepository.countSearchesSince(tenantId, since);
            analytics.put("totalSearches", searchCount);
            
            // Get top queries
            List<String> topQueries = searchRepository.findTopQueriesSince(tenantId, since, 10);
            analytics.put("topQueries", topQueries);
            
            // Get search volume by hour
            Map<String, Long> searchVolume = searchRepository.getSearchVolumeByHour(tenantId, since);
            analytics.put("searchVolumeByHour", searchVolume);
            
            // Get average response time
            double avgResponseTime = searchRepository.getAverageResponseTime(tenantId, since);
            analytics.put("averageResponseTimeMs", avgResponseTime);
            
            log.debug("Analytics generated for tenant: {}, searches: {}", tenantId, searchCount);
            return analytics;
            
        } catch (Exception e) {
            log.error("Failed to get analytics for tenant: {}, error: {}", tenantId, e.getMessage(), e);
            return Map.of("error", "Analytics temporarily unavailable");
        }
    }

    /**
     * Index a single document
     */
    @Timed(value = "search.index", description = "Document indexing time")
    public void indexDocument(DocumentSearchEntity document) {
        try {
            log.debug("Indexing document: {} for tenant: {}", document.getId(), document.getTenantId());
            
            searchRepository.save(document);
            metricsService.recordDocumentIndexing(document.getTenantId());
            
            log.info("Document indexed successfully: {}", document.getId());
            
        } catch (Exception e) {
            log.error("Failed to index document: {}, error: {}", document.getId(), e.getMessage(), e);
            throw new RuntimeException("Document indexing failed", e);
        }
    }

    /**
     * Bulk index multiple documents
     */
    @Timed(value = "search.bulk_index", description = "Bulk indexing time")
    public long bulkIndexDocuments(String tenantId, List<DocumentSearchEntity> documents) {
        try {
            log.debug("Bulk indexing {} documents for tenant: {}", documents.size(), tenantId);
            
            long startTime = System.currentTimeMillis();
            List<DocumentSearchEntity> saved = searchRepository.saveAll(documents);
            long duration = System.currentTimeMillis() - startTime;
            
            metricsService.recordBulkIndexing(tenantId, saved.size(), duration);
            
            log.info("Bulk indexed {} documents for tenant: {} in {}ms", 
                    saved.size(), tenantId, duration);
            
            return saved.size();
            
        } catch (Exception e) {
            log.error("Bulk indexing failed for tenant: {}, error: {}", tenantId, e.getMessage(), e);
            throw new RuntimeException("Bulk indexing failed", e);
        }
    }

    /**
     * Delete document from search index
     */
    public void deleteDocument(String tenantId, String documentId) {
        try {
            log.debug("Deleting document: {} for tenant: {}", documentId, tenantId);
            
            searchRepository.deleteByTenantIdAndDocumentId(tenantId, documentId);
            
            log.info("Document deleted from search index: {}", documentId);
            
        } catch (Exception e) {
            log.error("Failed to delete document: {}, error: {}", documentId, e.getMessage(), e);
            throw new RuntimeException("Document deletion failed", e);
        }
    }

    /**
     * Health check for search service
     */
    public boolean isHealthy() {
        try {
            // Simple health check - try to perform a basic query
            searchRepository.findTop1ByOrderByCreatedDateDesc();
            return true;
        } catch (Exception e) {
            log.warn("Search service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Clear cache for tenant
     */
    public void clearCache(String tenantId) {
        log.info("Clearing search cache for tenant: {}", tenantId);
        // Cache eviction would be handled by cache manager if configured
    }
}