package com.enterprise.documentsearch.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.enterprise.documentsearch.model.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for document operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private Long id;
    private String title;
    private String content;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String author;
    private List<String> tags;
    private String status;
    private String tenantId;
    private LocalDateTime createdDate;
    private LocalDateTime lastModifiedDate;
    private boolean successful;
    private String message;

    /**
     * Create from Document entity
     */
    public static DocumentResponse fromDocument(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .content(document.getContent())
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .author(document.getAuthor())
                .tags(document.getTags() != null ? 
                    List.of(document.getTags().split(",")) : 
                    List.of())
                .status(document.getStatus() != null ? document.getStatus().toString() : null)
                .tenantId(document.getTenantId())
                .createdDate(document.getCreatedDate())
                .lastModifiedDate(document.getLastModifiedDate())
                .successful(true)
                .message("Document retrieved successfully")
                .build();
    }
}