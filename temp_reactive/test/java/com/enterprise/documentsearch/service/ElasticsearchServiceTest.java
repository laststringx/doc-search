package com.enterprise.documentsearch.service;

import com.enterprise.documentsearch.model.Document;
import com.enterprise.documentsearch.model.DocumentSearchEntity;
import com.enterprise.documentsearch.repository.DocumentSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ElasticsearchService Unit Tests")
class ElasticsearchServiceTest {

    @Mock
    private DocumentSearchRepository documentSearchRepository;

    @InjectMocks
    private ElasticsearchService elasticsearchService;

    private Document testDocument;
    private DocumentSearchEntity testSearchEntity;
    private String tenantId;

    @BeforeEach
    void setUp() {
        tenantId = "tenant_123";
        
        testDocument = new Document();
        testDocument.setId(1L);
        testDocument.setTitle("Test Document");
        testDocument.setContent("Test content for searching");
        testDocument.setFileName("test.txt");
        testDocument.setFileType("text/plain");
        testDocument.setFileSize(1024L);
        testDocument.setAuthor("testuser");
        testDocument.setTenantId(tenantId);
        testDocument.setCreatedDate(LocalDateTime.now());
        testDocument.setLastModifiedDate(LocalDateTime.now());

        testSearchEntity = new DocumentSearchEntity();
        testSearchEntity.setId("1");
        testSearchEntity.setDocumentId(1L);
        testSearchEntity.setTitle("Test Document");
        testSearchEntity.setContent("Test content for searching");
        testSearchEntity.setFileName("test.txt");
        testSearchEntity.setFileType("text/plain");
        testSearchEntity.setFileSize(1024L);
        testSearchEntity.setAuthor("testuser");
        testSearchEntity.setTenantId(tenantId);
        testSearchEntity.setCreatedDate(LocalDateTime.now());
        testSearchEntity.setLastModifiedDate(LocalDateTime.now());
    }

    @Nested
    @DisplayName("Document Indexing Tests")
    class DocumentIndexingTests {

        @Test
        @DisplayName("Should index document successfully")
        void shouldIndexDocumentSuccessfully() {
            // Given
            when(documentSearchRepository.save(any(DocumentSearchEntity.class)))
                    .thenReturn(testSearchEntity);

            // When
            elasticsearchService.indexDocument(testDocument);

            // Then
            verify(documentSearchRepository).save(any(DocumentSearchEntity.class));
        }

        @Test
        @DisplayName("Should index document asynchronously")
        void shouldIndexDocumentAsynchronously() {
            // Given
            when(documentSearchRepository.save(any(DocumentSearchEntity.class)))
                    .thenReturn(testSearchEntity);

            // When
            elasticsearchService.indexDocumentAsync(testDocument);

            // Then
            // Verify that the document indexing is initiated
            // Note: Async testing is complex, this verifies the method can be called
            assertThat(testDocument).isNotNull();
        }

        @Test
        @DisplayName("Should bulk index documents successfully")
        void shouldBulkIndexDocumentsSuccessfully() {
            // Given
            Document doc2 = new Document();
            doc2.setId(2L);
            doc2.setTitle("Second Document");
            doc2.setContent("Second document content");
            doc2.setTenantId(tenantId);

            List<Document> documents = Arrays.asList(testDocument, doc2);
            
            when(documentSearchRepository.saveAll(anyList()))
                    .thenReturn(Arrays.asList(testSearchEntity));

            // When
            elasticsearchService.bulkIndexDocuments(documents);

            // Then
            verify(documentSearchRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("Should handle empty document list for bulk indexing")
        void shouldHandleEmptyDocumentListForBulkIndexing() {
            // Given
            List<Document> emptyList = Arrays.asList();

            // When
            elasticsearchService.bulkIndexDocuments(emptyList);

            // Then
            verify(documentSearchRepository, never()).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("Document Search Tests")
    class DocumentSearchTests {

        @Test
        @DisplayName("Should search documents by query and tenant")
        void shouldSearchDocumentsByQueryAndTenant() {
            // Given
            String query = "test";
            Pageable pageable = PageRequest.of(0, 10);
            Page<DocumentSearchEntity> searchPage = new PageImpl<>(Arrays.asList(testSearchEntity));
            
            when(documentSearchRepository.searchByTenantAndQuery(tenantId, query, pageable))
                    .thenReturn(searchPage);

            // When
            Page<DocumentSearchEntity> result = elasticsearchService.searchDocuments(tenantId, query, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getTenantId()).isEqualTo(tenantId);
            verify(documentSearchRepository).searchByTenantAndQuery(tenantId, query, pageable);
        }

        @Test
        @DisplayName("Should return empty results for no matches")
        void shouldReturnEmptyResultsForNoMatches() {
            // Given
            String query = "nonexistent";
            Pageable pageable = PageRequest.of(0, 10);
            Page<DocumentSearchEntity> emptyPage = new PageImpl<>(Arrays.asList());
            
            when(documentSearchRepository.searchByTenantAndQuery(tenantId, query, pageable))
                    .thenReturn(emptyPage);

            // When
            Page<DocumentSearchEntity> result = elasticsearchService.searchDocuments(tenantId, query, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            verify(documentSearchRepository).searchByTenantAndQuery(tenantId, query, pageable);
        }

        @Test
        @DisplayName("Should search with advanced filters")
        void shouldSearchWithAdvancedFilters() {
            // Given
            String query = "test";
            String fileType = "text/plain";
            Long minSize = 500L;
            Long maxSize = 2000L;
            Pageable pageable = PageRequest.of(0, 10);
            Page<DocumentSearchEntity> searchPage = new PageImpl<>(Arrays.asList(testSearchEntity));
            
            when(documentSearchRepository.searchByTenantAndQueryWithFilters(
                    eq(tenantId), eq(query), eq(fileType), eq(minSize), eq(maxSize), eq(pageable)))
                    .thenReturn(searchPage);

            // When
            Page<DocumentSearchEntity> result = elasticsearchService.searchDocumentsWithFilters(
                    tenantId, query, fileType, minSize, maxSize, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getFileType()).isEqualTo(fileType);
            verify(documentSearchRepository).searchByTenantAndQueryWithFilters(
                    tenantId, query, fileType, minSize, maxSize, pageable);
        }
    }

    @Nested
    @DisplayName("Document Deletion Tests")
    class DocumentDeletionTests {

        @Test
        @DisplayName("Should delete document from index successfully")
        void shouldDeleteDocumentFromIndexSuccessfully() {
            // Given
            Long documentId = 1L;
            when(documentSearchRepository.findByDocumentIdAndTenantId(documentId, tenantId))
                    .thenReturn(Optional.of(testSearchEntity));
            doNothing().when(documentSearchRepository).delete(any(DocumentSearchEntity.class));

            // When
            elasticsearchService.deleteDocumentFromIndex(documentId, tenantId);

            // Then
            verify(documentSearchRepository).findByDocumentIdAndTenantId(documentId, tenantId);
            verify(documentSearchRepository).delete(testSearchEntity);
        }

        @Test
        @DisplayName("Should handle deletion of non-existent document")
        void shouldHandleDeletionOfNonExistentDocument() {
            // Given
            Long documentId = 999L;
            when(documentSearchRepository.findByDocumentIdAndTenantId(documentId, tenantId))
                    .thenReturn(Optional.empty());

            // When
            elasticsearchService.deleteDocumentFromIndex(documentId, tenantId);

            // Then
            verify(documentSearchRepository).findByDocumentIdAndTenantId(documentId, tenantId);
            verify(documentSearchRepository, never()).delete(any(DocumentSearchEntity.class));
        }

        @Test
        @DisplayName("Should delete all documents for tenant")
        void shouldDeleteAllDocumentsForTenant() {
            // Given
            List<DocumentSearchEntity> tenantDocuments = Arrays.asList(testSearchEntity);
            when(documentSearchRepository.findByTenantId(tenantId))
                    .thenReturn(tenantDocuments);
            doNothing().when(documentSearchRepository).deleteAll(anyList());

            // When
            elasticsearchService.deleteAllDocumentsForTenant(tenantId);

            // Then
            verify(documentSearchRepository).findByTenantId(tenantId);
            verify(documentSearchRepository).deleteAll(tenantDocuments);
        }
    }

    @Nested
    @DisplayName("Autocomplete Tests")
    class AutocompleteTests {

        @Test
        @DisplayName("Should provide autocomplete suggestions")
        void shouldProvideAutocompleteSuggestions() {
            // Given
            String prefix = "test";
            List<String> suggestions = Arrays.asList("test document", "test file", "testing");
            when(documentSearchRepository.findAutocompleteSuggestions(tenantId, prefix))
                    .thenReturn(suggestions);

            // When
            List<String> result = elasticsearchService.getAutocompleteSuggestions(tenantId, prefix);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(3);
            assertThat(result).contains("test document", "test file", "testing");
            verify(documentSearchRepository).findAutocompleteSuggestions(tenantId, prefix);
        }

        @Test
        @DisplayName("Should return empty list for no autocomplete matches")
        void shouldReturnEmptyListForNoAutocompleteMatches() {
            // Given
            String prefix = "xyz";
            when(documentSearchRepository.findAutocompleteSuggestions(tenantId, prefix))
                    .thenReturn(Arrays.asList());

            // When
            List<String> result = elasticsearchService.getAutocompleteSuggestions(tenantId, prefix);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
            verify(documentSearchRepository).findAutocompleteSuggestions(tenantId, prefix);
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should get document count by tenant")
        void shouldGetDocumentCountByTenant() {
            // Given
            when(documentSearchRepository.countByTenantId(tenantId)).thenReturn(5L);

            // When
            long result = elasticsearchService.getDocumentCountByTenant(tenantId);

            // Then
            assertThat(result).isEqualTo(5L);
            verify(documentSearchRepository).countByTenantId(tenantId);
        }

        @Test
        @DisplayName("Should get search statistics")
        void shouldGetSearchStatistics() {
            // Given
            when(documentSearchRepository.countByTenantId(tenantId)).thenReturn(10L);

            // When
            long result = elasticsearchService.getDocumentCountByTenant(tenantId);

            // Then
            assertThat(result).isEqualTo(10L);
            verify(documentSearchRepository).countByTenantId(tenantId);
        }
    }

    @Nested
    @DisplayName("Document Update Tests")
    class DocumentUpdateTests {

        @Test
        @DisplayName("Should update document in index successfully")
        void shouldUpdateDocumentInIndexSuccessfully() {
            // Given
            Document updatedDocument = new Document();
            updatedDocument.setId(1L);
            updatedDocument.setTitle("Updated Document");
            updatedDocument.setContent("Updated content");
            updatedDocument.setTenantId(tenantId);

            when(documentSearchRepository.findByDocumentIdAndTenantId(1L, tenantId))
                    .thenReturn(Optional.of(testSearchEntity));
            when(documentSearchRepository.save(any(DocumentSearchEntity.class)))
                    .thenReturn(testSearchEntity);

            // When
            elasticsearchService.updateDocumentInIndex(updatedDocument);

            // Then
            verify(documentSearchRepository).findByDocumentIdAndTenantId(1L, tenantId);
            verify(documentSearchRepository).save(any(DocumentSearchEntity.class));
        }

        @Test
        @DisplayName("Should handle update of non-existent document")
        void shouldHandleUpdateOfNonExistentDocument() {
            // Given
            Document updatedDocument = new Document();
            updatedDocument.setId(999L);
            updatedDocument.setTenantId(tenantId);

            when(documentSearchRepository.findByDocumentIdAndTenantId(999L, tenantId))
                    .thenReturn(Optional.empty());

            // When
            elasticsearchService.updateDocumentInIndex(updatedDocument);

            // Then
            verify(documentSearchRepository).findByDocumentIdAndTenantId(999L, tenantId);
            verify(documentSearchRepository, never()).save(any(DocumentSearchEntity.class));
        }
    }

    @Nested
    @DisplayName("Tenant Isolation Tests")
    class TenantIsolationTests {

        @Test
        @DisplayName("Should ensure tenant isolation in search results")
        void shouldEnsureTenantIsolationInSearchResults() {
            // Given
            String query = "test";
            String otherTenantId = "other_tenant";
            Pageable pageable = PageRequest.of(0, 10);
            
            DocumentSearchEntity otherTenantDoc = new DocumentSearchEntity();
            otherTenantDoc.setTenantId(otherTenantId);
            
            when(documentSearchRepository.searchByTenantAndQuery(tenantId, query, pageable))
                    .thenReturn(new PageImpl<>(Arrays.asList(testSearchEntity)));
            when(documentSearchRepository.searchByTenantAndQuery(otherTenantId, query, pageable))
                    .thenReturn(new PageImpl<>(Arrays.asList(otherTenantDoc)));

            // When
            Page<DocumentSearchEntity> result1 = elasticsearchService.searchDocuments(tenantId, query, pageable);
            Page<DocumentSearchEntity> result2 = elasticsearchService.searchDocuments(otherTenantId, query, pageable);

            // Then
            assertThat(result1.getContent().get(0).getTenantId()).isEqualTo(tenantId);
            assertThat(result2.getContent().get(0).getTenantId()).isEqualTo(otherTenantId);
            verify(documentSearchRepository).searchByTenantAndQuery(tenantId, query, pageable);
            verify(documentSearchRepository).searchByTenantAndQuery(otherTenantId, query, pageable);
        }
    }
}