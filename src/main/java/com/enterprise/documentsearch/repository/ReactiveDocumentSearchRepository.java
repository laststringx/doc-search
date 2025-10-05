package com.enterprise.documentsearch.repository;

import com.enterprise.documentsearch.model.DocumentSearchEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Reactive Elasticsearch repository for high-performance document search
 * Optimized for 1000+ concurrent operations per second
 */
@Repository
public interface ReactiveDocumentSearchRepository extends ReactiveElasticsearchRepository<DocumentSearchEntity, String> {

    /**
     * Search documents by tenant and query with reactive streaming
     */
    @Query("{\"bool\": {\"must\": [{\"term\": {\"tenantId\": \"?0\"}}, {\"multi_match\": {\"query\": \"?1\", \"fields\": [\"title^3\", \"content^2\", \"tags\", \"fileName\"], \"type\": \"best_fields\", \"fuzziness\": \"AUTO\"}}]}}")
    Flux<DocumentSearchEntity> searchByTenantAndQuery(String tenantId, String query, Pageable pageable);

    /**
     * Count documents matching tenant and query
     */
    @Query("{\"bool\": {\"must\": [{\"term\": {\"tenantId\": \"?0\"}}, {\"multi_match\": {\"query\": \"?1\", \"fields\": [\"title^3\", \"content^2\", \"tags\", \"fileName\"], \"type\": \"best_fields\", \"fuzziness\": \"AUTO\"}}]}}")
    Mono<Long> countByTenantAndQuery(String tenantId, String query);

    /**
     * Advanced search with multiple filters
     */
    @Query("""
        {
          "bool": {
            "must": [
              {"term": {"tenantId": "?0"}},
              {"multi_match": {"query": "?1", "fields": ["title^3", "content^2", "tags", "fileName"]}}
            ],
            "filter": [
              ?#{#fileTypes != null && !#fileTypes.isEmpty() ? '{"terms": {"fileType": ' + #fileTypes + '}}' : '{"match_all": {}}'},
              ?#{#author != null ? '{"term": {"author.keyword": "' + #author + '"}}' : '{"match_all": {}}'},
              ?#{#startDate != null && #endDate != null ? '{"range": {"createdDate": {"gte": "' + #startDate + '", "lte": "' + #endDate + '"}}}' : '{"match_all": {}}'}
            ]
          }
        }
        """)
    Flux<DocumentSearchEntity> advancedSearch(
            String tenantId, String query, List<String> fileTypes, String author,
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Count documents for advanced search
     */
    @Query("""
        {
          "bool": {
            "must": [
              {"term": {"tenantId": "?0"}},
              {"multi_match": {"query": "?1", "fields": ["title^3", "content^2", "tags", "fileName"]}}
            ],
            "filter": [
              ?#{#fileTypes != null && !#fileTypes.isEmpty() ? '{"terms": {"fileType": ' + #fileTypes + '}}' : '{"match_all": {}}'},
              ?#{#author != null ? '{"term": {"author.keyword": "' + #author + '"}}' : '{"match_all": {}}'},
              ?#{#startDate != null && #endDate != null ? '{"range": {"createdDate": {"gte": "' + #startDate + '", "lte": "' + #endDate + '"}}}' : '{"match_all": {}}'}
            ]
          }
        }
        """)
    Mono<Long> countAdvancedSearch(
            String tenantId, String query, List<String> fileTypes, String author,
            LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find search suggestions for autocomplete
     */
    @Query("""
        {
          "bool": {
            "must": [
              {"term": {"tenantId": "?0"}},
              {"prefix": {"title": "?1"}}
            ]
          }
        }
        """)
    Flux<String> findSearchSuggestions(String tenantId, String prefix, int limit);

    /**
     * Get documents by tenant ID with streaming
     */
    Flux<DocumentSearchEntity> findByTenantId(String tenantId);

    /**
     * Count documents by tenant ID
     */
    Mono<Long> countByTenantId(String tenantId);

    /**
     * Find documents by tenant and file type
     */
    Flux<DocumentSearchEntity> findByTenantIdAndFileType(String tenantId, String fileType);

    /**
     * Find documents by tenant and author
     */
    Flux<DocumentSearchEntity> findByTenantIdAndAuthor(String tenantId, String author);

    /**
     * Find recent documents by tenant
     */
    @Query("{\"bool\": {\"must\": [{\"term\": {\"tenantId\": \"?0\"}}, {\"range\": {\"createdDate\": {\"gte\": \"?1\"}}}]}}")
    Flux<DocumentSearchEntity> findRecentDocuments(String tenantId, LocalDateTime since);

    /**
     * Get search analytics data
     */
    @Query("""
        {
          "bool": {
            "must": [
              {"term": {"tenantId": "?0"}},
              {"range": {"createdDate": {"gte": "?1"}}}
            ]
          },
          "aggs": {
            "file_types": {
              "terms": {"field": "fileType.keyword", "size": 10}
            },
            "authors": {
              "terms": {"field": "author.keyword", "size": 10}
            },
            "daily_counts": {
              "date_histogram": {
                "field": "createdDate",
                "calendar_interval": "day"
              }
            }
          }
        }
        """)
    Flux<Map.Entry<String, Object>> getSearchAnalytics(String tenantId, LocalDateTime since);

    /**
     * Find similar documents by content
     */
    @Query("""
        {
          "bool": {
            "must": [
              {"term": {"tenantId": "?0"}}
            ],
            "should": [
              {"more_like_this": {
                "fields": ["title", "content"],
                "like": "?1",
                "min_term_freq": 2,
                "max_query_terms": 25
              }}
            ]
          }
        }
        """)
    Flux<DocumentSearchEntity> findSimilarDocuments(String tenantId, String content, Pageable pageable);

    /**
     * Bulk delete documents by tenant ID
     */
    @Query("{\"term\": {\"tenantId\": \"?0\"}}")
    Mono<Void> deleteByTenantId(String tenantId);

    /**
     * Find documents with large file sizes for optimization
     */
    @Query("{\"bool\": {\"must\": [{\"term\": {\"tenantId\": \"?0\"}}, {\"range\": {\"fileSize\": {\"gte\": ?1}}}]}}")
    Flux<DocumentSearchEntity> findLargeDocuments(String tenantId, Long minSize);

    /**
     * Aggregate search statistics
     */
    @Query("""
        {
          "bool": {
            "must": [{"term": {"tenantId": "?0"}}]
          },
          "aggs": {
            "total_size": {"sum": {"field": "fileSize"}},
            "avg_size": {"avg": {"field": "fileSize"}},
            "max_size": {"max": {"field": "fileSize"}},
            "doc_count": {"value_count": {"field": "_id"}}
          }
        }
        """)
    Mono<Map<String, Object>> getDocumentStatistics(String tenantId);
}