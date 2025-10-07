package com.enterprise.documentsearch.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import com.enterprise.documentsearch.model.DocumentSearchEntity;

import java.util.List;

/**
 * Response DTO for search operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    private List<DocumentSearchEntity> documents;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private String query;
    private long searchTimeMs;
    private boolean successful;
    private String message;

    /**
     * Create from Spring Data Page
     */
    public static SearchResponse fromPage(Page<DocumentSearchEntity> page, String query, long searchTimeMs) {
        return SearchResponse.builder()
                .documents(page.getContent())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .query(query)
                .searchTimeMs(searchTimeMs)
                .successful(true)
                .message("Search completed successfully")
                .build();
    }
}