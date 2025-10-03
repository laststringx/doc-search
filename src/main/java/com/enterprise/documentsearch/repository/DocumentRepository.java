package com.enterprise.documentsearch.repository;

import com.enterprise.documentsearch.model.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    // Find documents by tenant
    Page<Document> findByTenantId(String tenantId, Pageable pageable);
    
    // Find document by tenant and id
    Optional<Document> findByTenantIdAndId(String tenantId, Long id);
    
    // Find documents by title containing (case insensitive)
    Page<Document> findByTenantIdAndTitleContainingIgnoreCase(String tenantId, String title, Pageable pageable);
    
    // Find documents by author
    Page<Document> findByTenantIdAndAuthor(String tenantId, String author, Pageable pageable);
    
    // Find documents by file type
    Page<Document> findByTenantIdAndFileType(String tenantId, String fileType, Pageable pageable);
    
    // Find documents created within date range
    Page<Document> findByTenantIdAndCreatedDateBetween(String tenantId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    // Custom query for full-text search
    @Query("SELECT d FROM Document d WHERE d.tenantId = :tenantId AND " +
           "(LOWER(d.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(d.content) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(d.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Document> searchDocuments(@Param("tenantId") String tenantId, 
                                   @Param("searchTerm") String searchTerm, 
                                   Pageable pageable);
    
    // Count documents by tenant
    long countByTenantId(String tenantId);
    
    // Find recent documents
    @Query("SELECT d FROM Document d WHERE d.tenantId = :tenantId ORDER BY d.lastModifiedDate DESC")
    List<Document> findRecentDocuments(@Param("tenantId") String tenantId, Pageable pageable);
    
    /**
     * Search documents by tenant and multiple fields
     */
    List<Document> findByTenantIdAndTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseOrContentContainingIgnoreCase(
        String tenantId, String title, String author, String content);
}