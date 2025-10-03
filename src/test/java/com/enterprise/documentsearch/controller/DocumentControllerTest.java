package com.enterprise.documentsearch.controller;

import com.enterprise.documentsearch.model.Document;
import com.enterprise.documentsearch.model.DocumentStatus;
import com.enterprise.documentsearch.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for DocumentController
 */
@WebMvcTest(DocumentController.class)
@DisplayName("Document Controller Tests")
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @Autowired
    private ObjectMapper objectMapper;

    private Document testDocument;
    private List<Document> testDocuments;

    @BeforeEach
    void setUp() {
        testDocument = new Document();
        testDocument.setId(1L);
        testDocument.setTitle("Test Document");
        testDocument.setContent("This is test content");
        testDocument.setAuthor("Test Author");
        testDocument.setFileName("test.pdf");
        testDocument.setFileType("PDF");
        testDocument.setFileSize(1024L);
        testDocument.setStatus(DocumentStatus.ACTIVE);
        testDocument.setTenantId("tenant_123");

        Document secondDocument = new Document();
        secondDocument.setId(2L);
        secondDocument.setTitle("Second Document");
        secondDocument.setContent("Second test content");
        secondDocument.setAuthor("Another Author");
        secondDocument.setFileName("test2.pdf");
        secondDocument.setFileType("PDF");
        secondDocument.setFileSize(2048L);
        secondDocument.setStatus(DocumentStatus.ACTIVE);
        secondDocument.setTenantId("tenant_123");

        testDocuments = Arrays.asList(testDocument, secondDocument);
    }

    @Nested
    @DisplayName("Get Documents Tests")
    class GetDocumentsTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should get documents with pagination")
        void shouldGetDocumentsWithPagination() throws Exception {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Document> documentPage = new PageImpl<>(testDocuments, pageable, testDocuments.size());
            
            when(documentService.findDocumentsByTenant(eq("tenant_123"), any(Pageable.class)))
                    .thenReturn(documentPage);

            // When & Then
            mockMvc.perform(get("/api/v1/documents")
                    .param("page", "0")
                    .param("size", "10")
                    .param("sortBy", "createdDate")
                    .param("sortDir", "desc")
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].title").value("Test Document"))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(1));

            verify(documentService).findDocumentsByTenant(eq("tenant_123"), any(Pageable.class));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should return empty page when no documents found")
        void shouldReturnEmptyPageWhenNoDocumentsFound() throws Exception {
            // Given
            when(documentService.findDocumentsByTenant(eq("tenant_123"), any(Pageable.class)))
                    .thenReturn(Page.empty());

            // When & Then
            mockMvc.perform(get("/api/v1/documents")
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0));

            verify(documentService).findDocumentsByTenant(eq("tenant_123"), any(Pageable.class));
        }

        @Test
        @DisplayName("Should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/documents")
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(documentService);
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should return 400 when tenant header is missing")
        void shouldReturn400WhenTenantHeaderMissing() throws Exception {
            mockMvc.perform(get("/api/v1/documents"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(documentService);
        }
    }

    @Nested
    @DisplayName("Get Document By ID Tests")
    class GetDocumentByIdTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should get document by ID successfully")
        void shouldGetDocumentByIdSuccessfully() throws Exception {
            // Given
            when(documentService.findDocument(eq("tenant_123"), eq(1L)))
                    .thenReturn(Optional.of(testDocument));

            // When & Then
            mockMvc.perform(get("/api/v1/documents/1")
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.title").value("Test Document"))
                    .andExpect(jsonPath("$.content").value("This is test content"));

            verify(documentService).findDocument(eq("tenant_123"), eq(1L));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should return 404 when document not found")
        void shouldReturn404WhenDocumentNotFound() throws Exception {
            // Given
            when(documentService.findDocument(eq("tenant_123"), eq(99L)))
                    .thenReturn(Optional.empty());

            // When & Then
            mockMvc.perform(get("/api/v1/documents/99")
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isNotFound());

            verify(documentService).findDocument(eq("tenant_123"), eq(99L));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should return 400 when document ID is invalid")
        void shouldReturn400WhenDocumentIdInvalid() throws Exception {
            mockMvc.perform(get("/api/v1/documents/invalid")
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(documentService);
        }
    }

    @Nested
    @DisplayName("Create Document Tests")
    class CreateDocumentTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should create document successfully")
        void shouldCreateDocumentSuccessfully() throws Exception {
            // Given
            Document requestDocument = new Document();
            requestDocument.setTitle("New Document");
            requestDocument.setContent("New content");
            requestDocument.setAuthor("Test Author");
            requestDocument.setTenantId("tenant_123");
            
            Document savedDocument = new Document();
            savedDocument.setId(3L);
            savedDocument.setTitle("New Document");
            savedDocument.setContent("New content");
            savedDocument.setAuthor("Test Author");
            savedDocument.setTenantId("tenant_123");
            savedDocument.setStatus(DocumentStatus.ACTIVE);

            when(documentService.createDocument(any(Document.class)))
                    .thenReturn(savedDocument);

            // When & Then
            mockMvc.perform(post("/api/v1/documents")
                    .header("X-Tenant-ID", "tenant_123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestDocument)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(3))
                    .andExpect(jsonPath("$.title").value("New Document"))
                    .andExpect(jsonPath("$.content").value("New content"));

            verify(documentService).createDocument(any(Document.class));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should return 400 when document is invalid")
        void shouldReturn400WhenDocumentInvalid() throws Exception {
            // Given - invalid document without title
            Document invalidDocument = new Document();
            invalidDocument.setContent("Content without title");

            // When & Then
            mockMvc.perform(post("/api/v1/documents")
                    .header("X-Tenant-ID", "tenant_123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidDocument)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(documentService);
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should handle service exceptions during creation")
        void shouldHandleServiceExceptionsDuringCreation() throws Exception {
            // Given
            Document requestDocument = new Document();
            requestDocument.setTitle("New Document");
            requestDocument.setContent("New content");
            requestDocument.setTenantId("tenant_123");

            when(documentService.createDocument(any(Document.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            mockMvc.perform(post("/api/v1/documents")
                    .header("X-Tenant-ID", "tenant_123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestDocument)))
                    .andExpect(status().isInternalServerError());

            verify(documentService).createDocument(any(Document.class));
        }
    }

    @Nested
    @DisplayName("Update Document Tests")
    class UpdateDocumentTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should update document successfully")
        void shouldUpdateDocumentSuccessfully() throws Exception {
            // Given
            Document updateDocument = new Document();
            updateDocument.setTitle("Updated Document");
            updateDocument.setContent("Updated content");
            updateDocument.setAuthor("Updated Author");

            Document updatedDocument = new Document();
            updatedDocument.setId(1L);
            updatedDocument.setTitle("Updated Document");
            updatedDocument.setContent("Updated content");
            updatedDocument.setAuthor("Updated Author");
            updatedDocument.setTenantId("tenant_123");
            updatedDocument.setStatus(DocumentStatus.ACTIVE);

            when(documentService.updateDocument(eq("tenant_123"), eq(1L), any(Document.class)))
                    .thenReturn(Optional.of(updatedDocument));

            // When & Then
            mockMvc.perform(put("/api/v1/documents/1")
                    .header("X-Tenant-ID", "tenant_123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateDocument)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.title").value("Updated Document"))
                    .andExpect(jsonPath("$.content").value("Updated content"));

            verify(documentService).updateDocument(eq("tenant_123"), eq(1L), any(Document.class));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should return 404 when document to update not found")
        void shouldReturn404WhenDocumentToUpdateNotFound() throws Exception {
            // Given
            Document updateDocument = new Document();
            updateDocument.setTitle("Updated Document");
            updateDocument.setContent("Updated content");

            when(documentService.updateDocument(eq("tenant_123"), eq(99L), any(Document.class)))
                    .thenReturn(Optional.empty());

            // When & Then
            mockMvc.perform(put("/api/v1/documents/99")
                    .header("X-Tenant-ID", "tenant_123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateDocument)))
                    .andExpect(status().isNotFound());

            verify(documentService).updateDocument(eq("tenant_123"), eq(99L), any(Document.class));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should return 400 when update data is invalid")
        void shouldReturn400WhenUpdateDataInvalid() throws Exception {
            // Given - invalid update data
            Document invalidUpdateDocument = new Document();
            invalidUpdateDocument.setTitle(""); // Empty title

            // When & Then
            mockMvc.perform(put("/api/v1/documents/1")
                    .header("X-Tenant-ID", "tenant_123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidUpdateDocument)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(documentService);
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should handle service exceptions during update")
        void shouldHandleServiceExceptionsDuringUpdate() throws Exception {
            // Given
            Document updateDocument = new Document();
            updateDocument.setTitle("Updated Document");
            updateDocument.setContent("Updated content");

            when(documentService.updateDocument(eq("tenant_123"), eq(1L), any(Document.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            mockMvc.perform(put("/api/v1/documents/1")
                    .header("X-Tenant-ID", "tenant_123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateDocument)))
                    .andExpect(status().isInternalServerError());

            verify(documentService).updateDocument(eq("tenant_123"), eq(1L), any(Document.class));
        }
    }

    @Nested
    @DisplayName("Delete Document Tests")
    class DeleteDocumentTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should delete document successfully")
        void shouldDeleteDocumentSuccessfully() throws Exception {
            // Given
            when(documentService.deleteDocument(eq("tenant_123"), eq(1L)))
                    .thenReturn(true);

            // When & Then
            mockMvc.perform(delete("/api/v1/documents/1")
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isNoContent());

            verify(documentService).deleteDocument(eq("tenant_123"), eq(1L));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should return 404 when document to delete not found")
        void shouldReturn404WhenDocumentToDeleteNotFound() throws Exception {
            // Given
            when(documentService.deleteDocument(eq("tenant_123"), eq(99L)))
                    .thenReturn(false);

            // When & Then
            mockMvc.perform(delete("/api/v1/documents/99")
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isNotFound());

            verify(documentService).deleteDocument(eq("tenant_123"), eq(99L));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should handle service exceptions during deletion")
        void shouldHandleServiceExceptionsDuringDeletion() throws Exception {
            // Given
            when(documentService.deleteDocument(eq("tenant_123"), eq(1L)))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            mockMvc.perform(delete("/api/v1/documents/1")
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isInternalServerError());

            verify(documentService).deleteDocument(eq("tenant_123"), eq(1L));
        }
    }

    @Nested
    @DisplayName("Document Statistics Tests")
    class DocumentStatisticsTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should get document count for tenant")
        void shouldGetDocumentCountForTenant() throws Exception {
            // Given
            when(documentService.countDocuments(eq("tenant_123")))
                    .thenReturn(42L);

            // When & Then
            mockMvc.perform(get("/api/v1/documents/stats/count")
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(42));

            verify(documentService).countDocuments(eq("tenant_123"));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should get document count by status for tenant")
        void shouldGetDocumentCountByStatusForTenant() throws Exception {
            // Given
            when(documentService.countDocuments(eq("tenant_123")))
                    .thenReturn(25L);

            // When & Then
            mockMvc.perform(get("/api/v1/documents/stats/count")
                    .param("status", "ACTIVE")
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(25));

            verify(documentService).countDocuments(eq("tenant_123"));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should handle service exceptions during statistics")
        void shouldHandleServiceExceptionsDuringStatistics() throws Exception {
            // Given
            when(documentService.countDocuments(eq("tenant_123")))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            mockMvc.perform(get("/api/v1/documents/stats/count")
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isInternalServerError());

            verify(documentService).countDocuments(eq("tenant_123"));
        }
    }
}