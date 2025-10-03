package com.enterprise.documentsearch.service;

import com.enterprise.documentsearch.model.Document;
import com.enterprise.documentsearch.model.DocumentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Service for processing uploaded files and extracting content
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileProcessingService {

    private final Tika tika = new Tika();
    
    // Supported file types with their MIME types
    private static final Map<String, List<String>> SUPPORTED_TYPES = Map.of(
        "Documents", Arrays.asList(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        ),
        "Text Files", Arrays.asList(
            "text/plain",
            "text/csv",
            "text/html",
            "text/xml",
            "application/json",
            "text/markdown"
        ),
        "Images", Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/bmp",
            "image/tiff"
        )
    );

    /**
     * Process uploaded file and create Document entity
     */
    public Document processUploadedFile(MultipartFile file, String tenantId, 
                                      String title, String author, String tags) {
        try {
            log.info("Processing file: {} (size: {} bytes)", file.getOriginalFilename(), file.getSize());

            // Validate file
            validateFile(file);

            // Extract content and metadata
            String content = extractContent(file);
            String detectedMimeType = detectMimeType(file);

            // Create document entity
            Document document = Document.builder()
                .tenantId(tenantId)
                .title(title != null ? title : generateTitleFromFilename(file.getOriginalFilename()))
                .content(content)
                .fileName(file.getOriginalFilename())
                .fileType(detectedMimeType)
                .fileSize(file.getSize())
                .author(author != null ? author : "Unknown")
                .tags(tags)
                .status(DocumentStatus.ACTIVE)
                .build();

            log.info("Successfully processed file: {} - extracted {} characters of content",
                file.getOriginalFilename(), content.length());

            return document;

        } catch (Exception e) {
            log.error("Failed to process file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("File processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extract text content from file using Apache Tika
     */
    private String extractContent(MultipartFile file) throws IOException, TikaException {
        try (InputStream inputStream = file.getInputStream()) {
            String content = tika.parseToString(inputStream);
            
            // Limit content size to prevent memory issues
            if (content.length() > 100000) { // 100KB limit
                content = content.substring(0, 100000) + "... [Content truncated]";
                log.warn("Content truncated for file: {} (original length: {})", 
                    file.getOriginalFilename(), content.length());
            }
            
            return content.trim();
        }
    }

    /**
     * Detect MIME type of the file
     */
    private String detectMimeType(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            String detectedType = tika.detect(inputStream, file.getOriginalFilename());
            log.debug("Detected MIME type: {} for file: {}", detectedType, file.getOriginalFilename());
            return detectedType;
        }
    }

    /**
     * Validate uploaded file
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > 50 * 1024 * 1024) { // 50MB limit
            throw new IllegalArgumentException("File size exceeds maximum limit of 50MB");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid filename");
        }

        // Check if file type is supported
        try {
            String mimeType = detectMimeType(file);
            if (!isSupportedMimeType(mimeType)) {
                throw new IllegalArgumentException("Unsupported file type: " + mimeType);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to determine file type", e);
        }
    }

    /**
     * Check if MIME type is supported
     */
    private boolean isSupportedMimeType(String mimeType) {
        return SUPPORTED_TYPES.values().stream()
            .flatMap(List::stream)
            .anyMatch(supportedType -> supportedType.equals(mimeType));
    }

    /**
     * Generate title from filename
     */
    private String generateTitleFromFilename(String filename) {
        if (filename == null) return "Untitled Document";
        
        // Remove extension and replace underscores/hyphens with spaces
        String title = filename.replaceAll("\\.[^.]*$", "")
                              .replaceAll("[_-]", " ")
                              .trim();
        
        // Capitalize first letter of each word
        String[] words = title.split("\\s+");
        StringBuilder capitalized = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                capitalized.append(word.substring(0, 1).toUpperCase())
                          .append(word.substring(1).toLowerCase())
                          .append(" ");
            }
        }
        
        return capitalized.toString().trim();
    }

    /**
     * Get supported file types information
     */
    public Map<String, Object> getSupportedFileTypes() {
        Map<String, Object> response = new HashMap<>();
        response.put("supportedTypes", SUPPORTED_TYPES);
        response.put("maxFileSize", "50MB");
        response.put("maxContentLength", "100KB");
        
        List<String> allMimeTypes = SUPPORTED_TYPES.values().stream()
            .flatMap(List::stream)
            .sorted()
            .toList();
        response.put("allSupportedMimeTypes", allMimeTypes);
        
        return response;
    }


}