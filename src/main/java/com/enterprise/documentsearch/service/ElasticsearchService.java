package com.enterprise.documentsearch.service;

import com.enterprise.documentsearch.model.Document;
import com.enterprise.documentsearch.model.DocumentSearchEntity;
import com.enterprise.documentsearch.repository.DocumentSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Elasticsearch service for high-performance document search operations.
 * Handles full-text search, indexing, and search-related caching.
 * Disabled for test profile when Elasticsearch is not available.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Profile("!test")
public class ElasticsearchService {

    private final DocumentSearchRepository documentSearchRepository;
    private final MetricsService metricsService;

    /**
     * Index a document in Elasticsearch for search operations
     */
    @Async("documentIndexingExecutor")
    public CompletableFuture<Boolean> indexDocumentAsync(Document document) {
        long start = System.currentTimeMillis();
        try {
            log.info("Starting async indexing for document: {} in tenant: {}", 
                    document.getId(), document.getTenantId());

            DocumentSearchEntity searchEntity = convertToSearchEntity(document);
            documentSearchRepository.save(searchEntity);

            long duration = System.currentTimeMillis() - start;
            metricsService.recordDocumentIndex(document.getTenantId(), duration, true);
            metricsService.recordElasticsearchOperation("index", duration, true);

            log.info("Successfully indexed document: {} in tenant: {} ({}ms)", 
                    document.getId(), document.getTenantId(), duration);
            return CompletableFuture.completedFuture(true);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            metricsService.recordDocumentIndex(document.getTenantId(), duration, false);
            metricsService.recordElasticsearchOperation("index", duration, false);
            
            log.error("Failed to index document: {} in tenant: {}", 
                    document.getId(), document.getTenantId(), e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Synchronous indexing for immediate consistency
     */
    public boolean indexDocument(Document document) {
        long start = System.currentTimeMillis();
        try {
            log.debug("Indexing document: {} in tenant: {}", document.getId(), document.getTenantId());

            DocumentSearchEntity searchEntity = convertToSearchEntity(document);
            documentSearchRepository.save(searchEntity);

            long duration = System.currentTimeMillis() - start;
            metricsService.recordDocumentIndex(document.getTenantId(), duration, true);
            metricsService.recordElasticsearchOperation("index", duration, true);

            return true;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            metricsService.recordDocumentIndex(document.getTenantId(), duration, false);
            metricsService.recordElasticsearchOperation("index", duration, false);
            
            log.error("Failed to index document: {} in tenant: {}", 
                    document.getId(), document.getTenantId(), e);
            return false;
        }
    }

    /**
     * Full-text search with caching
     */
    @Cacheable(value = "searchResults", key = "#tenantId + ':' + #query + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<DocumentSearchEntity> searchDocuments(String tenantId, String query, Pageable pageable) {
        long start = System.currentTimeMillis();
        try {
            log.debug("Searching documents for tenant: {} with query: '{}'", tenantId, query);

            Page<DocumentSearchEntity> results = documentSearchRepository.searchByTenantAndQuery(tenantId, query, pageable);

            long duration = System.currentTimeMillis() - start;
            metricsService.recordDocumentSearch(tenantId, duration, true);
            metricsService.recordElasticsearchOperation("search", duration, true);

            log.debug("Found {} documents for tenant: {} query: '{}' ({}ms)", 
                    results.getTotalElements(), tenantId, query, duration);

            return results;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            metricsService.recordDocumentSearch(tenantId, duration, false);
            metricsService.recordElasticsearchOperation("search", duration, false);
            
            log.error("Search failed for tenant: {} query: '{}'", tenantId, query, e);
            throw new RuntimeException("Search operation failed", e);
        }
    }

    /**
     * Advanced search with filters
     */
    @Cacheable(value = "searchResults", key = "#tenantId + ':advanced:' + #query + ':' + #fileTypes + ':' + #startDate + ':' + #endDate")
    public Page<DocumentSearchEntity> advancedSearch(String tenantId, String query, List<String> fileTypes, 
                                                   LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        long start = System.currentTimeMillis();
        try {
            log.debug("Advanced search for tenant: {} with query: '{}', types: {}", tenantId, query, fileTypes);

            String startDateStr = startDate != null ? startDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;
            String endDateStr = endDate != null ? endDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;

            Page<DocumentSearchEntity> results = documentSearchRepository.advancedSearch(
                    tenantId, query, fileTypes, startDateStr, endDateStr, pageable);

            long duration = System.currentTimeMillis() - start;
            metricsService.recordDocumentSearch(tenantId, duration, true);
            metricsService.recordElasticsearchOperation("advanced_search", duration, true);

            return results;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            metricsService.recordDocumentSearch(tenantId, duration, false);
            metricsService.recordElasticsearchOperation("advanced_search", duration, false);
            
            log.error("Advanced search failed for tenant: {}", tenantId, e);
            throw new RuntimeException("Advanced search operation failed", e);
        }
    }

    /**
     * Get search suggestions for autocomplete
     */
    @Cacheable(value = "searchResults", key = "#tenantId + ':suggestions:' + #titlePrefix")
    public List<DocumentSearchEntity> getTitleSuggestions(String tenantId, String titlePrefix) {
        try {
            return documentSearchRepository.findTitleSuggestions(tenantId, titlePrefix);
        } catch (Exception e) {
            log.error("Failed to get suggestions for tenant: {} prefix: '{}'", tenantId, titlePrefix, e);
            return List.of();
        }
    }

    /**
     * Delete document from search index
     */
    public boolean deleteDocument(String tenantId, Long documentId) {
        try {
            log.debug("Deleting document from search index: {} in tenant: {}", documentId, tenantId);
            documentSearchRepository.deleteByTenantIdAndDocumentId(tenantId, documentId);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete document from search index: {} in tenant: {}", 
                    documentId, tenantId, e);
            return false;
        }
    }

    /**
     * Bulk index multiple documents
     */
    @Async("documentIndexingExecutor")
    public CompletableFuture<Integer> bulkIndexDocuments(List<Document> documents) {
        long start = System.currentTimeMillis();
        int successCount = 0;
        
        try {
            log.info("Starting bulk indexing for {} documents", documents.size());

            List<DocumentSearchEntity> searchEntities = documents.stream()
                    .map(this::convertToSearchEntity)
                    .toList();

            documentSearchRepository.saveAll(searchEntities);
            successCount = searchEntities.size();

            long duration = System.currentTimeMillis() - start;
            log.info("Bulk indexed {}/{} documents ({}ms)", successCount, documents.size(), duration);

            return CompletableFuture.completedFuture(successCount);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Bulk indexing failed after {}ms, indexed {}/{} documents", 
                    duration, successCount, documents.size(), e);
            return CompletableFuture.completedFuture(successCount);
        }
    }

    /**
     * Get document count for tenant
     */
    @Cacheable(value = "documentMetadata", key = "#tenantId + ':count'")
    public long getDocumentCount(String tenantId) {
        return documentSearchRepository.countByTenantId(tenantId);
    }

    /**
     * Convert Document entity to DocumentSearchEntity
     */
    private DocumentSearchEntity convertToSearchEntity(Document document) {
        DocumentSearchEntity searchEntity = DocumentSearchEntity.builder()
                .tenantId(document.getTenantId())
                .documentId(document.getId())
                .title(document.getTitle())
                .content(document.getContent())
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .author(document.getAuthor())
                .tags(document.getTags())
                .status(document.getStatus() != null ? document.getStatus().name() : null)
                .createdDate(document.getCreatedDate())
                .lastModifiedDate(document.getLastModifiedDate())
                .build();
        
        searchEntity.setCompositeId();
        return searchEntity;
    }
}