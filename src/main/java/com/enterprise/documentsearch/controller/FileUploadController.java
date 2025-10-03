package com.enterprise.documentsearch.controller;

import com.enterprise.documentsearch.model.Document;
import com.enterprise.documentsearch.model.User;
import com.enterprise.documentsearch.service.DocumentService;
import com.enterprise.documentsearch.service.FileProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * File upload and processing controller
 */
@RestController
@RequestMapping("/api/v1/upload")
@Tag(name = "File Upload", description = "File upload and document processing endpoints")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private final FileProcessingService fileProcessingService;
    private final DocumentService documentService;

    /**
     * Upload and process a document file
     */
    @PostMapping("/document")
    @Operation(summary = "Upload document", description = "Upload and process a document file (PDF, Word, Text, etc.)")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "author", required = false) String author,
            @RequestParam(value = "tags", required = false) String tags) {

        User user = (User) authentication.getPrincipal();
        String tenantId = user.getTenantId();

        log.info("Processing file upload: {} for tenant: {}", file.getOriginalFilename(), tenantId);

        try {
            // Process the uploaded file
            Document document = fileProcessingService.processUploadedFile(
                file, tenantId, title, author, tags);

            // Save the document
            Document savedDocument = documentService.createDocument(document);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "File uploaded and processed successfully");
            response.put("document", savedDocument);
            response.put("fileSize", file.getSize());
            response.put("fileName", file.getOriginalFilename());
            response.put("contentType", file.getContentType());

            log.info("File upload completed successfully: {} (ID: {})", 
                savedDocument.getFileName(), savedDocument.getId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("File upload failed for file: {}", file.getOriginalFilename(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "File upload failed");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("fileName", file.getOriginalFilename());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Upload multiple documents
     */
    @PostMapping("/documents/batch")
    @Operation(summary = "Batch upload documents", description = "Upload and process multiple document files")
    public ResponseEntity<Map<String, Object>> uploadDocuments(
            Authentication authentication,
            @RequestParam("files") MultipartFile[] files) {

        User user = (User) authentication.getPrincipal();
        String tenantId = user.getTenantId();

        log.info("Processing batch upload of {} files for tenant: {}", files.length, tenantId);

        Map<String, Object> response = new HashMap<>();
        int successCount = 0;
        int failureCount = 0;

        for (MultipartFile file : files) {
            try {
                Document document = fileProcessingService.processUploadedFile(
                    file, tenantId, null, null, null);
                documentService.createDocument(document);
                successCount++;
                log.info("Successfully processed file: {}", file.getOriginalFilename());
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to process file: {}", file.getOriginalFilename(), e);
            }
        }

        response.put("message", "Batch upload completed");
        response.put("totalFiles", files.length);
        response.put("successCount", successCount);
        response.put("failureCount", failureCount);

        return ResponseEntity.ok(response);
    }

    /**
     * Get supported file types
     */
    @GetMapping("/supported-types")
    @Operation(summary = "Get supported file types", description = "Get list of supported file types for upload")
    public ResponseEntity<Map<String, Object>> getSupportedFileTypes() {
        Map<String, Object> response = fileProcessingService.getSupportedFileTypes();
        return ResponseEntity.ok(response);
    }
}