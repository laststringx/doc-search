package com.enterprise.documentsearch.repository;

import com.enterprise.documentsearch.model.DocumentSearchEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Elasticsearch repository for full-text search operations on documents.
 * Provides high-performance search capabilities for enterprise-scale document management.
 */
@Repository
public interface DocumentSearchRepository extends ElasticsearchRepository<DocumentSearchEntity, String> {

    /**
     * Find documents by tenant ID with pagination
     */
    Page<DocumentSearchEntity> findByTenantId(String tenantId, Pageable pageable);

    /**
     * Find document by tenant ID and document ID
     */
    Optional<DocumentSearchEntity> findByTenantIdAndDocumentId(String tenantId, Long documentId);

    /**
     * Delete document by tenant ID and document ID
     */
    void deleteByTenantIdAndDocumentId(String tenantId, Long documentId);

    /**
     * Full-text search across title and content with tenant isolation
     */
    @Query("""
        {
            "bool": {
                "must": [
                    {
                        "term": {
                            "tenantId": "?0"
                        }
                    },
                    {
                        "multi_match": {
                            "query": "?1",
                            "fields": ["title^2", "content", "tags"],
                            "type": "best_fields",
                            "fuzziness": "AUTO"
                        }
                    }
                ]
            }
        }
        """)
    Page<DocumentSearchEntity> searchByTenantAndQuery(String tenantId, String query, Pageable pageable);

    /**
     * Search by file type with tenant isolation
     */
    @Query("""
        {
            "bool": {
                "must": [
                    {
                        "term": {
                            "tenantId": "?0"
                        }
                    },
                    {
                        "term": {
                            "fileType": "?1"
                        }
                    }
                ]
            }
        }
        """)
    Page<DocumentSearchEntity> findByTenantIdAndFileType(String tenantId, String fileType, Pageable pageable);

    /**
     * Search by author with tenant isolation
     */
    @Query("""
        {
            "bool": {
                "must": [
                    {
                        "term": {
                            "tenantId": "?0"
                        }
                    },
                    {
                        "match": {
                            "author": "?1"
                        }
                    }
                ]
            }
        }
        """)
    Page<DocumentSearchEntity> findByTenantIdAndAuthor(String tenantId, String author, Pageable pageable);

    /**
     * Advanced search with multiple filters
     */
    @Query("""
        {
            "bool": {
                "must": [
                    {
                        "term": {
                            "tenantId": "?0"
                        }
                    },
                    {
                        "multi_match": {
                            "query": "?1",
                            "fields": ["title^2", "content", "tags"],
                            "type": "best_fields",
                            "fuzziness": "AUTO"
                        }
                    }
                ],
                "filter": [
                    {
                        "terms": {
                            "fileType": "?2"
                        }
                    },
                    {
                        "range": {
                            "createdDate": {
                                "gte": "?3",
                                "lte": "?4"
                            }
                        }
                    }
                ]
            }
        }
        """)
    Page<DocumentSearchEntity> advancedSearch(String tenantId, String query, List<String> fileTypes, 
                                            String startDate, String endDate, Pageable pageable);

    /**
     * Get suggestions for autocomplete
     */
    @Query("""
        {
            "bool": {
                "must": [
                    {
                        "term": {
                            "tenantId": "?0"
                        }
                    },
                    {
                        "match_phrase_prefix": {
                            "title": "?1"
                        }
                    }
                ]
            }
        }
        """)
    List<DocumentSearchEntity> findTitleSuggestions(String tenantId, String titlePrefix);

    /**
     * Count documents by tenant
     */
    long countByTenantId(String tenantId);

    /**
     * Count documents by tenant and file type
     */
    long countByTenantIdAndFileType(String tenantId, String fileType);
}