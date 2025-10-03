package com.enterprise.documentsearch.controller;

import com.enterprise.documentsearch.model.Document;
import com.enterprise.documentsearch.model.User;
import com.enterprise.documentsearch.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents", description = "Document management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {
    
    private final DocumentService documentService;
    
    /**
     * Get all documents for authenticated user's tenant
     */
    @GetMapping
    @Operation(summary = "Get documents", description = "Get paginated list of documents for authenticated user's tenant")
    public ResponseEntity<Map<String, Object>> getDocuments(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "lastModifiedDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        User user = (User) authentication.getPrincipal();
        String tenantId = user.getTenantId();
        
        log.info("Getting documents for tenant: {} with page: {}, size: {}", tenantId, page, size);
        
        Sort sort = sortDir.equalsIgnoreCase("desc") ? 
            Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Document> documents = documentService.findDocumentsByTenant(tenantId, pageable);
        
        Map<String, Object> response = new HashMap<>();
        response.put("documents", documents.getContent());
        response.put("currentPage", documents.getNumber());
        response.put("totalItems", documents.getTotalElements());
        response.put("totalPages", documents.getTotalPages());
        response.put("pageSize", documents.getSize());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Create a new document
     */
    @PostMapping
    @Operation(summary = "Create document", description = "Create a new document for authenticated user's tenant")
    public ResponseEntity<Document> createDocument(
            Authentication authentication,
            @Valid @RequestBody Document document) {
        
        User user = (User) authentication.getPrincipal();
        String tenantId = user.getTenantId();
        
        log.info("Creating document for tenant: {}", tenantId);
        
        document.setTenantId(tenantId);
        Document savedDocument = documentService.createDocument(document);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(savedDocument);
    }
    
    /**
     * Get a specific document by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get document", description = "Get a specific document by ID for authenticated user's tenant")
    public ResponseEntity<Document> getDocument(
            Authentication authentication,
            @PathVariable Long id) {
        
        User user = (User) authentication.getPrincipal();
        String tenantId = user.getTenantId();
        
        log.info("Getting document with id: {} for tenant: {}", id, tenantId);
        
        Optional<Document> document = documentService.findDocumentByIdAndTenant(id, tenantId);
        
        return document.map(ResponseEntity::ok)
                      .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Update a document
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update document", description = "Update a document for authenticated user's tenant")
    public ResponseEntity<Document> updateDocument(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody Document document) {
        
        User user = (User) authentication.getPrincipal();
        String tenantId = user.getTenantId();
        
        log.info("Updating document with id: {} for tenant: {}", id, tenantId);
        
        Optional<Document> updatedDocument = documentService.updateDocument(id, tenantId, document);
        
        return updatedDocument.map(ResponseEntity::ok)
                             .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Delete a document
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete document", description = "Delete a document for authenticated user's tenant")
    public ResponseEntity<Void> deleteDocument(
            Authentication authentication,
            @PathVariable Long id) {
        
        User user = (User) authentication.getPrincipal();
        String tenantId = user.getTenantId();
        
        log.info("Deleting document with id: {} for tenant: {}", id, tenantId);
        
        boolean deleted = documentService.deleteDocument(id, tenantId);
        
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
    
    /**
     * Search documents
     */
    @GetMapping("/search")
    @Operation(summary = "Search documents", description = "Search documents for authenticated user's tenant")
    public ResponseEntity<List<Document>> searchDocuments(
            Authentication authentication,
            @RequestParam String query) {
        
        User user = (User) authentication.getPrincipal();
        String tenantId = user.getTenantId();
        
        log.info("Searching documents for tenant: {} with query: {}", tenantId, query);
        
        List<Document> documents = documentService.searchDocuments(tenantId, query);
        
        return ResponseEntity.ok(documents);
    }
    
    /**
     * Get document count for authenticated user's tenant
     */
    @GetMapping("/count")
    @Operation(summary = "Get document count", description = "Get total document count for authenticated user's tenant")
    public ResponseEntity<Map<String, Object>> getDocumentCount(
            Authentication authentication) {
        
        User user = (User) authentication.getPrincipal();
        String tenantId = user.getTenantId();
        
        log.info("Getting document count for tenant: {}", tenantId);
        
        long count = documentService.getDocumentCountByTenant(tenantId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("documentCount", count);
        response.put("tenantId", tenantId);
        
        return ResponseEntity.ok(response);
    }
}