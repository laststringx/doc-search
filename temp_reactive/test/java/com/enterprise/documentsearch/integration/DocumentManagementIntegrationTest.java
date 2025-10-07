package com.enterprise.documentsearch.integration;

import com.enterprise.documentsearch.model.Document;
import com.enterprise.documentsearch.model.DocumentStatus;
import com.enterprise.documentsearch.model.User;
import com.enterprise.documentsearch.model.Role;
import com.enterprise.documentsearch.repository.DocumentRepository;
import com.enterprise.documentsearch.repository.UserRepository;
import com.enterprise.documentsearch.security.JwtTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Document Management Integration Tests")
class DocumentManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String authToken;
    private String tenantId = "tenant_123";

    @BeforeEach
    void setUp() {
        // Clean up database
        documentRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = new User();
        testUser.setUsername("integrationuser");
        testUser.setEmail("integration@example.com");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser.setFirstName("Integration");
        testUser.setLastName("User");
        testUser.setTenantId(tenantId);
        testUser.setActive(true);
        testUser.setEmailVerified(true);
        testUser.setRoles(Set.of(Role.USER));
        testUser.setCreatedDate(LocalDateTime.now());
        testUser.setLastModifiedDate(LocalDateTime.now());
        testUser = userRepository.save(testUser);

        // Generate auth token
        authToken = jwtTokenUtil.generateToken(testUser);
    }

    @Nested
    @DisplayName("Document CRUD Operations")
    class DocumentCrudOperations {

        @Test
        @Transactional
        @DisplayName("Should create, read, update, and delete document")
        void shouldPerformCompleteDocumentLifecycle() throws Exception {
            // 1. CREATE DOCUMENT
            Map<String, Object> createRequest = new HashMap<>();
            createRequest.put("title", "Integration Test Document");
            createRequest.put("content", "This is integration test content");
            createRequest.put("author", "Integration Author");
            createRequest.put("status", "ACTIVE");

            String createResponse = mockMvc.perform(post("/api/v1/documents")
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("Integration Test Document"))
                    .andExpect(jsonPath("$.content").value("This is integration test content"))
                    .andExpect(jsonPath("$.author").value("Integration Author"))
                    .andExpect(jsonPath("$.tenantId").value(tenantId))
                    .andReturn().getResponse().getContentAsString();

            Document createdDocument = objectMapper.readValue(createResponse, Document.class);
            Long documentId = createdDocument.getId();

            // 2. READ DOCUMENT
            mockMvc.perform(get("/api/v1/documents/" + documentId)
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(documentId))
                    .andExpect(jsonPath("$.title").value("Integration Test Document"))
                    .andExpect(jsonPath("$.content").value("This is integration test content"));

            // 3. UPDATE DOCUMENT
            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("title", "Updated Integration Test Document");
            updateRequest.put("content", "Updated integration test content");

            mockMvc.perform(put("/api/v1/documents/" + documentId)
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Updated Integration Test Document"))
                    .andExpect(jsonPath("$.content").value("Updated integration test content"));

            // 4. DELETE DOCUMENT
            mockMvc.perform(delete("/api/v1/documents/" + documentId)
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isNoContent());

            // 5. VERIFY DELETION
            mockMvc.perform(get("/api/v1/documents/" + documentId)
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @Transactional
        @DisplayName("Should handle document pagination correctly")
        void shouldHandleDocumentPaginationCorrectly() throws Exception {
            // Create multiple documents
            for (int i = 1; i <= 15; i++) {
                Document document = new Document();
                document.setTitle("Document " + i);
                document.setContent("Content " + i);
                document.setAuthor("Author " + i);
                document.setStatus(DocumentStatus.ACTIVE);
                document.setTenantId(tenantId);
                document.setCreatedDate(LocalDateTime.now());
                document.setLastModifiedDate(LocalDateTime.now());
                documentRepository.save(document);
            }

            // Test first page
            mockMvc.perform(get("/api/v1/documents")
                    .param("page", "0")
                    .param("size", "10")
                    .param("sortBy", "createdDate")
                    .param("sortDir", "desc")
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(10))
                    .andExpect(jsonPath("$.totalElements").value(15))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.first").value(true))
                    .andExpect(jsonPath("$.last").value(false));

            // Test second page
            mockMvc.perform(get("/api/v1/documents")
                    .param("page", "1")
                    .param("size", "10")
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(5))
                    .andExpect(jsonPath("$.first").value(false))
                    .andExpect(jsonPath("$.last").value(true));
        }
    }

    @Nested
    @DisplayName("Document Search Operations")
    class DocumentSearchOperations {

        @Test
        @Transactional
        @DisplayName("Should search documents by title and content")
        void shouldSearchDocumentsByTitleAndContent() throws Exception {
            // Create test documents
            Document doc1 = new Document();
            doc1.setTitle("Java Programming Guide");
            doc1.setContent("This document covers Java programming concepts");
            doc1.setAuthor("Java Author");
            doc1.setStatus(DocumentStatus.ACTIVE);
            doc1.setTenantId(tenantId);
            doc1.setCreatedDate(LocalDateTime.now());
            doc1.setLastModifiedDate(LocalDateTime.now());
            documentRepository.save(doc1);

            Document doc2 = new Document();
            doc2.setTitle("Python Tutorial");
            doc2.setContent("Learn Python programming with examples");
            doc2.setAuthor("Python Author");
            doc2.setStatus(DocumentStatus.ACTIVE);
            doc2.setTenantId(tenantId);
            doc2.setCreatedDate(LocalDateTime.now());
            doc2.setLastModifiedDate(LocalDateTime.now());
            documentRepository.save(doc2);

            Document doc3 = new Document();
            doc3.setTitle("Database Design");
            doc3.setContent("Database design principles and Java integration");
            doc3.setAuthor("DB Author");
            doc3.setStatus(DocumentStatus.ACTIVE);
            doc3.setTenantId(tenantId);
            doc3.setCreatedDate(LocalDateTime.now());
            doc3.setLastModifiedDate(LocalDateTime.now());
            documentRepository.save(doc3);

            // Search for "Java" - should find 2 documents
            mockMvc.perform(get("/api/v1/documents/search")
                    .param("query", "Java")
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpected(jsonPath("$.totalElements").value(2));

            // Search for "Programming" - should find 2 documents
            mockMvc.perform(get("/api/v1/documents/search")
                    .param("query", "Programming")
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2));

            // Search for "Database" - should find 1 document
            mockMvc.perform(get("/api/v1/documents/search")
                    .param("query", "Database")
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("Database Design"));

            // Search for non-existent term
            mockMvc.perform(get("/api/v1/documents/search")
                    .param("query", "NonExistent")
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("Tenant Isolation Tests")
    class TenantIsolationTests {

        @Test
        @Transactional
        @DisplayName("Should enforce tenant isolation for documents")
        void shouldEnforceTenantIsolationForDocuments() throws Exception {
            // Create documents for different tenants
            Document tenant1Doc = new Document();
            tenant1Doc.setTitle("Tenant 1 Document");
            tenant1Doc.setContent("Content for tenant 1");
            tenant1Doc.setStatus(DocumentStatus.ACTIVE);
            tenant1Doc.setTenantId(tenantId);
            tenant1Doc.setCreatedDate(LocalDateTime.now());
            tenant1Doc.setLastModifiedDate(LocalDateTime.now());
            tenant1Doc = documentRepository.save(tenant1Doc);

            Document tenant2Doc = new Document();
            tenant2Doc.setTitle("Tenant 2 Document");
            tenant2Doc.setContent("Content for tenant 2");
            tenant2Doc.setStatus(DocumentStatus.ACTIVE);
            tenant2Doc.setTenantId("tenant_456");
            tenant2Doc.setCreatedDate(LocalDateTime.now());
            tenant2Doc.setLastModifiedDate(LocalDateTime.now());
            tenant2Doc = documentRepository.save(tenant2Doc);

            // User from tenant_123 should only see their document
            mockMvc.perform(get("/api/v1/documents")
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("Tenant 1 Document"));

            // User should not be able to access document from another tenant
            mockMvc.perform(get("/api/v1/documents/" + tenant2Doc.getId())
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isNotFound());

            // User should not be able to update document from another tenant
            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("title", "Hacked Title");

            mockMvc.perform(put("/api/v1/documents/" + tenant2Doc.getId())
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isNotFound());

            // User should not be able to delete document from another tenant
            mockMvc.perform(delete("/api/v1/documents/" + tenant2Doc.getId())
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Document Count Operations")
    class DocumentCountOperations {

        @Test
        @Transactional
        @DisplayName("Should count documents correctly")
        void shouldCountDocumentsCorrectly() throws Exception {
            // Create documents with different statuses
            for (int i = 1; i <= 5; i++) {
                Document document = new Document();
                document.setTitle("Active Document " + i);
                document.setContent("Content " + i);
                document.setStatus(DocumentStatus.ACTIVE);
                document.setTenantId(tenantId);
                document.setCreatedDate(LocalDateTime.now());
                document.setLastModifiedDate(LocalDateTime.now());
                documentRepository.save(document);
            }

            for (int i = 1; i <= 3; i++) {
                Document document = new Document();
                document.setTitle("Draft Document " + i);
                document.setContent("Draft Content " + i);
                document.setStatus(DocumentStatus.DRAFT);
                document.setTenantId(tenantId);
                document.setCreatedDate(LocalDateTime.now());
                document.setLastModifiedDate(LocalDateTime.now());
                documentRepository.save(document);
            }

            // Count all documents
            mockMvc.perform(get("/api/v1/documents/count")
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(8));

            // Count active documents
            mockMvc.perform(get("/api/v1/documents/count")
                    .param("status", "ACTIVE")
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(5));

            // Count draft documents
            mockMvc.perform(get("/api/v1/documents/count")
                    .param("status", "DRAFT")
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(3));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle missing authentication")
        void shouldHandleMissingAuthentication() throws Exception {
            mockMvc.perform(get("/api/v1/documents"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should handle missing tenant ID")
        void shouldHandleMissingTenantId() throws Exception {
            mockMvc.perform(get("/api/v1/documents")
                    .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle invalid document ID")
        void shouldHandleInvalidDocumentId() throws Exception {
            mockMvc.perform(get("/api/v1/documents/999999")
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should handle validation errors")
        void shouldHandleValidationErrors() throws Exception {
            Map<String, Object> invalidRequest = new HashMap<>();
            // Missing required title field

            mockMvc.perform(post("/api/v1/documents")
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-Tenant-ID", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }
    }
}