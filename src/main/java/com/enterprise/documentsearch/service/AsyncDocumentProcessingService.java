package com.enterprise.documentsearch.service;

import com.enterprise.documentsearch.model.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

/**
 * Async document processing service for handling high-volume 
 * document operations without blocking main threads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncDocumentProcessingService {

    private final FileProcessingService fileProcessingService;
    private final DocumentService documentService;

    /**
     * Asynchronously process file upload and document creation
     */
    @Async("fileProcessingExecutor")
    public CompletableFuture<Document> processFileUploadAsync(MultipartFile file, String tenantId, String title, String author, String tags) {
        try {
            log.info("Starting async file processing for: {} in tenant: {}", file.getOriginalFilename(), tenantId);
            
            // Process the file using existing service
            Document document = fileProcessingService.processUploadedFile(file, tenantId, title, author, tags);
            
            log.info("Successfully processed file: {} and created document: {}", 
                    file.getOriginalFilename(), document.getId());
            return CompletableFuture.completedFuture(document);
            
        } catch (Exception e) {
            log.error("Failed to process file: {}", file.getOriginalFilename(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Asynchronously save document
     */
    @Async("documentIndexingExecutor")
    public CompletableFuture<Document> saveDocumentAsync(Document document) {
        try {
            log.info("Starting async save for document: {} in tenant: {}", 
                    document.getTitle(), document.getTenantId());
            
            Document savedDocument = documentService.saveDocument(document);
            
            log.info("Successfully saved document: {} in tenant: {}", 
                    savedDocument.getId(), savedDocument.getTenantId());
            return CompletableFuture.completedFuture(savedDocument);
            
        } catch (Exception e) {
            log.error("Failed to save document: {} in tenant: {}", 
                    document.getTitle(), document.getTenantId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Asynchronously process bulk document operations
     */
    @Async("documentIndexingExecutor")
    public CompletableFuture<Integer> processBulkDocumentsAsync(String tenantId, java.util.List<Document> documents) {
        try {
            log.info("Starting bulk processing for {} documents in tenant: {}", documents.size(), tenantId);
            
            int processedCount = 0;
            for (Document document : documents) {
                try {
                    // Save to database
                    documentService.saveDocument(document);
                    processedCount++;
                } catch (Exception e) {
                    log.warn("Failed to process document {} in bulk operation: {}", 
                            document.getTitle(), e.getMessage());
                }
            }
            
            log.info("Bulk processing completed. Processed {}/{} documents in tenant: {}", 
                    processedCount, documents.size(), tenantId);
            return CompletableFuture.completedFuture(processedCount);
            
        } catch (Exception e) {
            log.error("Failed bulk processing for tenant: {}", tenantId, e);
            return CompletableFuture.completedFuture(0);
        }
    }

    /**
     * Asynchronously create document with validation
     */
    @Async("documentIndexingExecutor")
    public CompletableFuture<Document> createDocumentAsync(String tenantId, String title, String content, String author) {
        try {
            log.info("Starting async document creation: {} in tenant: {}", title, tenantId);
            
            Document document = Document.builder()
                    .tenantId(tenantId)
                    .title(title)
                    .content(content)
                    .author(author)
                    .status(com.enterprise.documentsearch.model.DocumentStatus.ACTIVE)
                    .build();
            
            Document savedDocument = documentService.saveDocument(document);
            
            log.info("Successfully created document: {} in tenant: {}", 
                    savedDocument.getId(), savedDocument.getTenantId());
            return CompletableFuture.completedFuture(savedDocument);
            
        } catch (Exception e) {
            log.error("Failed to create document: {} in tenant: {}", title, tenantId, e);
            return CompletableFuture.failedFuture(e);
        }
    }
}