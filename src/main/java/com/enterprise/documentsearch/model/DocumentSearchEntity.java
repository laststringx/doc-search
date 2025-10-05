package com.enterprise.documentsearch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * Elasticsearch document entity for full-text search operations.
 * This represents the searchable version of documents stored in the primary database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "documents")
public class DocumentSearchEntity {

    @Id
    private String id; // Combination of tenantId:documentId for uniqueness
    
    @Field(type = FieldType.Keyword)
    private String tenantId;
    
    @Field(type = FieldType.Long)
    private Long documentId;
    
    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;
    
    @Field(type = FieldType.Text, analyzer = "standard")
    private String content;
    
    @Field(type = FieldType.Keyword)
    private String fileName;
    
    @Field(type = FieldType.Keyword)
    private String fileType;
    
    @Field(type = FieldType.Long)
    private Long fileSize;
    
    @Field(type = FieldType.Keyword)
    private String author;
    
    @Field(type = FieldType.Text, analyzer = "keyword")
    private String tags;
    
    @Field(type = FieldType.Keyword)
    private String status;
    
    @Field(type = FieldType.Date)
    private LocalDateTime createdDate;
    
    @Field(type = FieldType.Date)
    private LocalDateTime lastModifiedDate;
    
    /**
     * Generate unique ID for Elasticsearch document
     */
    public static String generateId(String tenantId, Long documentId) {
        return tenantId + ":" + documentId;
    }
    
    /**
     * Set the composite ID
     */
    public void setCompositeId() {
        this.id = generateId(this.tenantId, this.documentId);
    }
}