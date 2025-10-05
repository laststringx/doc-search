package com.enterprise.documentsearch.service;

import com.enterprise.documentsearch.model.Document;
import com.enterprise.documentsearch.repository.DocumentRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@Transactional
public class DocumentService {
    
    private final DocumentRepository documentRepository;
    private final Optional<ElasticsearchService> elasticsearchService;
    
    public DocumentService(DocumentRepository documentRepository, Optional<ElasticsearchService> elasticsearchService) {
        this.documentRepository = documentRepository;
        this.elasticsearchService = elasticsearchService;
    }
    
    /**
     * Save a document with cache eviction and update
     */
    @CachePut(value = "documents", key = "#document.tenantId + ':' + #document.id")
    @CacheEvict(value = "searchResults", allEntries = true) // Clear search cache when document changes
    public Document saveDocument(Document document) {
        log.info("Saving document with title: {} for tenant: {}", document.getTitle(), document.getTenantId());
        Document savedDocument = documentRepository.save(document);
        
        // Index in Elasticsearch asynchronously if available
        elasticsearchService.ifPresent(service -> service.indexDocumentAsync(savedDocument));
        
        return savedDocument;
    }
    
    /**
     * Find document by tenant and id with caching
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "documents", key = "#tenantId + ':' + #id")
    public Optional<Document> findDocument(String tenantId, Long id) {
        log.debug("Finding document with id: {} for tenant: {}", id, tenantId);
        return documentRepository.findByTenantIdAndId(tenantId, id);
    }
    
    /**
     * Find all documents for a tenant with pagination and caching
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "documentMetadata", key = "#tenantId + ':page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize")
    public Page<Document> findDocumentsByTenant(String tenantId, Pageable pageable) {
        log.debug("Finding documents for tenant: {} with pagination", tenantId);
        return documentRepository.findByTenantId(tenantId, pageable);
    }
    
    /**
     * Search documents by text
     */
    @Transactional(readOnly = true)
    public Page<Document> searchDocuments(String tenantId, String searchTerm, Pageable pageable) {
        log.info("Searching documents for tenant: {} with term: {}", tenantId, searchTerm);
        return documentRepository.searchDocuments(tenantId, searchTerm, pageable);
    }
    
    /**
     * Get recent documents
     */
    @Transactional(readOnly = true)
    public List<Document> getRecentDocuments(String tenantId, Pageable pageable) {
        log.debug("Getting recent documents for tenant: {}", tenantId);
        return documentRepository.findRecentDocuments(tenantId, pageable);
    }
    
    /**
     * Delete document
     */
    public boolean deleteDocument(String tenantId, Long id) {
        log.info("Deleting document with id: {} for tenant: {}", id, tenantId);
        Optional<Document> document = documentRepository.findByTenantIdAndId(tenantId, id);
        if (document.isPresent()) {
            documentRepository.delete(document.get());
            return true;
        }
        return false;
    }
    
    /**
     * Count documents for tenant
     */
    @Transactional(readOnly = true)
    public long countDocuments(String tenantId) {
        return documentRepository.countByTenantId(tenantId);
    }
    
    /**
     * Update document
     */
    public Optional<Document> updateDocument(String tenantId, Long id, Document updatedDocument) {
        log.info("Updating document with id: {} for tenant: {}", id, tenantId);
        Optional<Document> existingDocument = documentRepository.findByTenantIdAndId(tenantId, id);
        
        if (existingDocument.isPresent()) {
            Document document = existingDocument.get();
            document.setTitle(updatedDocument.getTitle());
            document.setContent(updatedDocument.getContent());
            document.setFileName(updatedDocument.getFileName());
            document.setFileType(updatedDocument.getFileType());
            document.setFileSize(updatedDocument.getFileSize());
            document.setAuthor(updatedDocument.getAuthor());
            document.setTags(updatedDocument.getTags());
            document.setStatus(updatedDocument.getStatus());
            
            return Optional.of(documentRepository.save(document));
        }
        
        return Optional.empty();
    }

    // Additional methods for the new controller signatures
    
    /**
     * Create a new document
     */
    public Document createDocument(Document document) {
        log.info("Creating document with title: {} for tenant: {}", document.getTitle(), document.getTenantId());
        return documentRepository.save(document);
    }
    
    /**
     * Find document by ID and tenant
     */
    @Transactional(readOnly = true)
    public Optional<Document> findDocumentByIdAndTenant(Long id, String tenantId) {
        log.debug("Finding document with id: {} for tenant: {}", id, tenantId);
        return documentRepository.findByTenantIdAndId(tenantId, id);
    }
    
    /**
     * Update document with ID first parameter
     */
    public Optional<Document> updateDocument(Long id, String tenantId, Document updatedDocument) {
        return updateDocument(tenantId, id, updatedDocument);
    }
    
    /**
     * Delete document with ID first parameter
     */
    public boolean deleteDocument(Long id, String tenantId) {
        return deleteDocument(tenantId, id);
    }
    
    /**
     * Search documents without pagination
     */
    @Transactional(readOnly = true)
    public List<Document> searchDocuments(String tenantId, String searchTerm) {
        log.info("Searching documents for tenant: {} with term: {}", tenantId, searchTerm);
        return documentRepository.findByTenantIdAndTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseOrContentContainingIgnoreCase(
            tenantId, searchTerm, searchTerm, searchTerm);
    }
    
    /**
     * Get document count by tenant
     */
    @Transactional(readOnly = true)
    public long getDocumentCountByTenant(String tenantId) {
        return countDocuments(tenantId);
    }
}