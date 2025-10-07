package com.enterprise.documentsearch.service;

import com.enterprise.documentsearch.model.Document;
import com.enterprise.documentsearch.model.DocumentStatus;
import com.enterprise.documentsearch.repository.DocumentRepository;
import com.enterprise.documentsearch.exception.ResourceNotFoundException;
import com.enterprise.documentsearch.exception.BusinessException;
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
@DisplayName("DocumentService Unit Tests")
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private DocumentService documentService;

    private Document testDocument;
    private String tenantId;

    @BeforeEach
    void setUp() {
        tenantId = "tenant_123";
        
        testDocument = new Document();
        testDocument.setId(1L);
        testDocument.setTitle("Test Document");
        testDocument.setContent("Test content");
        testDocument.setAuthor("Test Author");
        testDocument.setFileName("test.pdf");
        testDocument.setFileType("PDF");
        testDocument.setFileSize(1024L);
        testDocument.setStatus(DocumentStatus.ACTIVE);
        testDocument.setTenantId(tenantId);
        testDocument.setTags("test,document");
        testDocument.setCreatedDate(LocalDateTime.now());
        testDocument.setLastModifiedDate(LocalDateTime.now());
        testDocument.setVersion(1L);
    }

    @Nested
    @DisplayName("Create Document Tests")
    class CreateDocumentTests {

        @Test
        @DisplayName("Should create document successfully")
        void shouldCreateDocumentSuccessfully() {
            // Given
            Document newDocument = new Document();
            newDocument.setTitle("New Document");
            newDocument.setContent("New content");
            newDocument.setAuthor("New Author");
            newDocument.setStatus(DocumentStatus.ACTIVE);

            Document savedDocument = new Document();
            savedDocument.setId(2L);
            savedDocument.setTitle("New Document");
            savedDocument.setContent("New content");
            savedDocument.setAuthor("New Author");
            savedDocument.setStatus(DocumentStatus.ACTIVE);
            savedDocument.setTenantId(tenantId);
            savedDocument.setCreatedDate(LocalDateTime.now());
            savedDocument.setLastModifiedDate(LocalDateTime.now());

            when(documentRepository.save(any(Document.class))).thenReturn(savedDocument);

            // When
            Document result = documentService.createDocument(newDocument, tenantId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(2L);
            assertThat(result.getTitle()).isEqualTo("New Document");
            assertThat(result.getTenantId()).isEqualTo(tenantId);
            assertThat(result.getCreatedDate()).isNotNull();
            assertThat(result.getLastModifiedDate()).isNotNull();

            verify(documentRepository).save(any(Document.class));
        }

        @Test
        @DisplayName("Should set default values when creating document")
        void shouldSetDefaultValuesWhenCreatingDocument() {
            // Given
            Document newDocument = new Document();
            newDocument.setTitle("New Document");
            newDocument.setContent("New content");

            when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
                Document doc = invocation.getArgument(0);
                doc.setId(3L);
                return doc;
            });

            // When
            Document result = documentService.createDocument(newDocument, tenantId);

            // Then
            assertThat(result.getTenantId()).isEqualTo(tenantId);
            assertThat(result.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
            assertThat(result.getCreatedDate()).isNotNull();
            assertThat(result.getLastModifiedDate()).isNotNull();

            verify(documentRepository).save(argThat(doc -> 
                doc.getTenantId().equals(tenantId) && 
                doc.getStatus() == DocumentStatus.ACTIVE &&
                doc.getCreatedDate() != null &&
                doc.getLastModifiedDate() != null
            ));
        }

        @Test
        @DisplayName("Should throw exception when creating document with null title")
        void shouldThrowExceptionWhenCreatingDocumentWithNullTitle() {
            // Given
            Document newDocument = new Document();
            newDocument.setContent("New content");

            // When & Then
            assertThatThrownBy(() -> documentService.createDocument(newDocument, tenantId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Title is required");

            verifyNoInteractions(documentRepository);
        }

        @Test
        @DisplayName("Should throw exception when creating document with empty tenant ID")
        void shouldThrowExceptionWhenCreatingDocumentWithEmptyTenantId() {
            // Given
            Document newDocument = new Document();
            newDocument.setTitle("New Document");
            newDocument.setContent("New content");

            // When & Then
            assertThatThrownBy(() -> documentService.createDocument(newDocument, ""))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Tenant ID is required");

            verifyNoInteractions(documentRepository);
        }
    }

    @Nested
    @DisplayName("Find Document Tests")
    class FindDocumentTests {

        @Test
        @DisplayName("Should find document by ID and tenant successfully")
        void shouldFindDocumentByIdAndTenantSuccessfully() {
            // Given
            when(documentRepository.findByIdAndTenantId(1L, tenantId))
                    .thenReturn(Optional.of(testDocument));

            // When
            Document result = documentService.findByIdAndTenant(1L, tenantId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getTitle()).isEqualTo("Test Document");
            assertThat(result.getTenantId()).isEqualTo(tenantId);

            verify(documentRepository).findByIdAndTenantId(1L, tenantId);
        }

        @Test
        @DisplayName("Should throw exception when document not found")
        void shouldThrowExceptionWhenDocumentNotFound() {
            // Given
            when(documentRepository.findByIdAndTenantId(999L, tenantId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> documentService.findByIdAndTenant(999L, tenantId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Document not found");

            verify(documentRepository).findByIdAndTenantId(999L, tenantId);
        }

        @Test
        @DisplayName("Should find all documents by tenant with pagination")
        void shouldFindAllDocumentsByTenantWithPagination() {
            // Given
            Document document2 = new Document();
            document2.setId(2L);
            document2.setTitle("Another Document");
            document2.setTenantId(tenantId);

            List<Document> documents = Arrays.asList(testDocument, document2);
            Pageable pageable = PageRequest.of(0, 10);
            Page<Document> documentPage = new PageImpl<>(documents, pageable, documents.size());

            when(documentRepository.findByTenantId(tenantId, pageable))
                    .thenReturn(documentPage);

            // When
            Page<Document> result = documentService.findAllByTenant(tenantId, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Test Document");
            assertThat(result.getContent().get(1).getTitle()).isEqualTo("Another Document");

            verify(documentRepository).findByTenantId(tenantId, pageable);
        }

        @Test
        @DisplayName("Should return empty page when no documents found")
        void shouldReturnEmptyPageWhenNoDocumentsFound() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Document> emptyPage = new PageImpl<>(Arrays.asList(), pageable, 0);

            when(documentRepository.findByTenantId(tenantId, pageable))
                    .thenReturn(emptyPage);

            // When
            Page<Document> result = documentService.findAllByTenant(tenantId, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);

            verify(documentRepository).findByTenantId(tenantId, pageable);
        }
    }

    @Nested
    @DisplayName("Update Document Tests")
    class UpdateDocumentTests {

        @Test
        @DisplayName("Should update document successfully")
        void shouldUpdateDocumentSuccessfully() {
            // Given
            Document updateData = new Document();
            updateData.setTitle("Updated Title");
            updateData.setContent("Updated content");
            updateData.setAuthor("Updated Author");

            when(documentRepository.findByIdAndTenantId(1L, tenantId))
                    .thenReturn(Optional.of(testDocument));
            when(documentRepository.save(any(Document.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Document result = documentService.updateDocument(1L, updateData, tenantId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Updated Title");
            assertThat(result.getContent()).isEqualTo("Updated content");
            assertThat(result.getAuthor()).isEqualTo("Updated Author");
            assertThat(result.getLastModifiedDate()).isNotNull();
            assertThat(result.getVersion()).isEqualTo(2L); // Version incremented

            verify(documentRepository).findByIdAndTenantId(1L, tenantId);
            verify(documentRepository).save(any(Document.class));
        }

        @Test
        @DisplayName("Should throw exception when updating non-existent document")
        void shouldThrowExceptionWhenUpdatingNonExistentDocument() {
            // Given
            Document updateData = new Document();
            updateData.setTitle("Updated Title");

            when(documentRepository.findByIdAndTenantId(999L, tenantId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> documentService.updateDocument(999L, updateData, tenantId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Document not found");

            verify(documentRepository).findByIdAndTenant(999L, tenantId);
            verify(documentRepository, never()).save(any(Document.class));
        }

        @Test
        @DisplayName("Should only update non-null fields")
        void shouldOnlyUpdateNonNullFields() {
            // Given
            Document updateData = new Document();
            updateData.setTitle("Updated Title");
            // content and author are null, should not be updated

            when(documentRepository.findByIdAndTenantId(1L, tenantId))
                    .thenReturn(Optional.of(testDocument));
            when(documentRepository.save(any(Document.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Document result = documentService.updateDocument(1L, updateData, tenantId);

            // Then
            assertThat(result.getTitle()).isEqualTo("Updated Title");
            assertThat(result.getContent()).isEqualTo("Test content"); // Original value preserved
            assertThat(result.getAuthor()).isEqualTo("Test Author"); // Original value preserved

            verify(documentRepository).save(any(Document.class));
        }
    }

    @Nested
    @DisplayName("Delete Document Tests")
    class DeleteDocumentTests {

        @Test
        @DisplayName("Should delete document successfully")
        void shouldDeleteDocumentSuccessfully() {
            // Given
            when(documentRepository.findByIdAndTenantId(1L, tenantId))
                    .thenReturn(Optional.of(testDocument));

            // When
            boolean result = documentService.deleteDocument(1L, tenantId);

            // Then
            assertThat(result).isTrue();

            verify(documentRepository).findByIdAndTenantId(1L, tenantId);
            verify(documentRepository).delete(testDocument);
        }

        @Test
        @DisplayName("Should return false when deleting non-existent document")
        void shouldReturnFalseWhenDeletingNonExistentDocument() {
            // Given
            when(documentRepository.findByIdAndTenantId(999L, tenantId))
                    .thenReturn(Optional.empty());

            // When
            boolean result = documentService.deleteDocument(999L, tenantId);

            // Then
            assertThat(result).isFalse();

            verify(documentRepository).findByIdAndTenantId(999L, tenantId);
            verify(documentRepository, never()).delete(any(Document.class));
        }

        @Test
        @DisplayName("Should perform soft delete for important documents")
        void shouldPerformSoftDeleteForImportantDocuments() {
            // Given
            testDocument.setStatus(DocumentStatus.ACTIVE);
            testDocument.setTags("important,critical");

            when(documentRepository.findByIdAndTenantId(1L, tenantId))
                    .thenReturn(Optional.of(testDocument));
            when(documentRepository.save(any(Document.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            boolean result = documentService.deleteDocument(1L, tenantId);

            // Then
            assertThat(result).isTrue();
            assertThat(testDocument.getStatus()).isEqualTo(DocumentStatus.DELETED);

            verify(documentRepository).save(testDocument);
            verify(documentRepository, never()).delete(any(Document.class));
        }
    }

    @Nested
    @DisplayName("Search Document Tests")
    class SearchDocumentTests {

        @Test
        @DisplayName("Should search documents by query successfully")
        void shouldSearchDocumentsByQuerySuccessfully() {
            // Given
            String query = "test";
            Pageable pageable = PageRequest.of(0, 10);
            List<Document> searchResults = Arrays.asList(testDocument);
            Page<Document> searchPage = new PageImpl<>(searchResults, pageable, searchResults.size());

            when(documentRepository.findByTenantIdAndTitleContainingOrContentContaining(
                    eq(tenantId), eq(query), eq(query), eq(pageable)))
                    .thenReturn(searchPage);

            // When
            Page<Document> result = documentService.searchDocuments(query, tenantId, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Test Document");

            verify(documentRepository).findByTenantIdAndTitleContainingOrContentContaining(
                    tenantId, query, query, pageable);
        }

        @Test
        @DisplayName("Should return empty results for no matches")
        void shouldReturnEmptyResultsForNoMatches() {
            // Given
            String query = "nonexistent";
            Pageable pageable = PageRequest.of(0, 10);
            Page<Document> emptyPage = new PageImpl<>(Arrays.asList(), pageable, 0);

            when(documentRepository.findByTenantIdAndTitleContainingOrContentContaining(
                    eq(tenantId), eq(query), eq(query), eq(pageable)))
                    .thenReturn(emptyPage);

            // When
            Page<Document> result = documentService.searchDocuments(query, tenantId, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);

            verify(documentRepository).findByTenantIdAndTitleContainingOrContentContaining(
                    tenantId, query, query, pageable);
        }

        @Test
        @DisplayName("Should throw exception for empty search query")
        void shouldThrowExceptionForEmptySearchQuery() {
            // Given
            String emptyQuery = "";
            Pageable pageable = PageRequest.of(0, 10);

            // When & Then
            assertThatThrownBy(() -> documentService.searchDocuments(emptyQuery, tenantId, pageable))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Search query cannot be empty");

            verifyNoInteractions(documentRepository);
        }
    }

    @Nested
    @DisplayName("Count Document Tests")
    class CountDocumentTests {

        @Test
        @DisplayName("Should count documents by tenant successfully")
        void shouldCountDocumentsByTenantSuccessfully() {
            // Given
            when(documentRepository.countByTenantId(tenantId))
                    .thenReturn(5L);

            // When
            Long result = documentService.countDocumentsByTenant(tenantId);

            // Then
            assertThat(result).isEqualTo(5L);

            verify(documentRepository).countByTenantId(tenantId);
        }

        @Test
        @DisplayName("Should count documents by tenant and status successfully")
        void shouldCountDocumentsByTenantAndStatusSuccessfully() {
            // Given
            when(documentRepository.countByTenantIdAndStatus(tenantId, DocumentStatus.ACTIVE))
                    .thenReturn(3L);

            // When
            Long result = documentService.countDocumentsByTenantAndStatus(tenantId, DocumentStatus.ACTIVE);

            // Then
            assertThat(result).isEqualTo(3L);

            verify(documentRepository).countByTenantIdAndStatus(tenantId, DocumentStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should return zero when no documents found")
        void shouldReturnZeroWhenNoDocumentsFound() {
            // Given
            when(documentRepository.countByTenantId("empty_tenant"))
                    .thenReturn(0L);

            // When
            Long result = documentService.countDocumentsByTenant("empty_tenant");

            // Then
            assertThat(result).isEqualTo(0L);

            verify(documentRepository).countByTenantId("empty_tenant");
        }
    }
}