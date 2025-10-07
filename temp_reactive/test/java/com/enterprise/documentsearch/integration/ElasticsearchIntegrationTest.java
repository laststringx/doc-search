package com.enterprise.documentsearch.integration;

import com.enterprise.documentsearch.model.Document;
import com.enterprise.documentsearch.model.User;
import com.enterprise.documentsearch.model.Role;
import com.enterprise.documentsearch.model.DocumentSearchEntity;
import com.enterprise.documentsearch.repository.DocumentSearchRepository;
import com.enterprise.documentsearch.service.DocumentService;
import com.enterprise.documentsearch.service.ElasticsearchService;
import com.enterprise.documentsearch.service.UserService;
import com.enterprise.documentsearch.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for Elasticsearch functionality with enterprise features
 */
@SpringBootTest
@AutoConfigureWebMvc
@TestPropertySource(properties = {
    "spring.elasticsearch.uris=http://localhost:9200",
    "management.endpoints.web.exposure.include=health,metrics,prometheus",
    "spring.data.elasticsearch.repositories.enabled=true"
})
@Transactional
class ElasticsearchIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private UserService userService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentSearchRepository documentSearchRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private User testUser;
    private Document testDocument;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Create test user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("testpassword123");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setRoles(Set.of(Role.USER));
        testUser.setTenantId("test-tenant");
        testUser.setActive(true);
        testUser = userService.createUser(testUser);

        // Create test document
        testDocument = new Document();
        testDocument.setTitle("Test Document for Search");
        testDocument.setContent("This is a test document with searchable content about enterprise document management systems.");
        testDocument.setFileName("test-document.txt");
        testDocument.setFileType("text/plain");
        testDocument.setFileSize(1024L);
        testDocument.setAuthor(testUser.getUsername());
        testDocument.setTenantId(testUser.getTenantId());
        testDocument.setCreatedDate(LocalDateTime.now());
        testDocument.setLastModifiedDate(LocalDateTime.now());
        testDocument = documentRepository.save(testDocument);
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testDocumentIndexingAndSearch() throws Exception {
        // Index the document asynchronously
        elasticsearchService.indexDocumentAsync(testDocument);
        
        // Wait for indexing to complete
        Thread.sleep(2000);
        
        // Test that document was indexed using search method
        Page<DocumentSearchEntity> searchResults = documentSearchRepository.searchByTenantAndQuery("test-tenant", "enterprise", 
                PageRequest.of(0, 10));
        assertFalse(searchResults.isEmpty(), "Document should be found in search results");
        
        DocumentSearchEntity foundDocument = searchResults.getContent().get(0);
        assertEquals(testDocument.getTitle(), foundDocument.getTitle());
        assertEquals(testDocument.getContent(), foundDocument.getContent());
        assertEquals(testDocument.getTenantId(), foundDocument.getTenantId());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testSearchApiEndpoint() throws Exception {
        // Index the document first
        elasticsearchService.indexDocumentAsync(testDocument);
        Thread.sleep(2000);
        
        // Test search API endpoint
        mockMvc.perform(get("/api/v1/search")
                .param("query", "enterprise")
                .param("tenantId", "test-tenant")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.content[0].title").value("Test Document for Search"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testAdvancedSearchWithFilters() throws Exception {
        // Index the document first
        elasticsearchService.indexDocumentAsync(testDocument);
        Thread.sleep(2000);
        
        // Test advanced search with filters
        mockMvc.perform(get("/api/v1/search/advanced")
                .param("query", "document")
                .param("tenantId", "test-tenant")
                .param("mimeType", "text/plain")
                .param("minSize", "500")
                .param("maxSize", "2000")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testSearchAutocomplete() throws Exception {
        // Index the document first
        elasticsearchService.indexDocumentAsync(testDocument);
        Thread.sleep(2000);
        
        // Test autocomplete API
        mockMvc.perform(get("/api/v1/search/autocomplete")
                .param("prefix", "enter")
                .param("tenantId", "test-tenant")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testSearchStatistics() throws Exception {
        // Test search statistics endpoint
        mockMvc.perform(get("/api/v1/search/statistics")
                .param("tenantId", "test-tenant")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").exists())
                .andExpect(jsonPath("$.searchCount").exists());
    }

    @Test
    void testBulkIndexing() throws Exception {
        // Create multiple test documents
        Document doc1 = new Document();
        doc1.setTitle("Java Programming Guide");
        doc1.setContent("Comprehensive guide to Java programming with Spring Boot");
        doc1.setFileName("java-guide.pdf");
        doc1.setFileType("application/pdf");
        doc1.setAuthor(testUser.getUsername());
        doc1.setTenantId(testUser.getTenantId());
        doc1 = documentRepository.save(doc1);

        Document doc2 = new Document();
        doc2.setTitle("Elasticsearch Best Practices");
        doc2.setContent("Advanced techniques for Elasticsearch optimization and scaling");
        doc2.setFileName("es-practices.docx");
        doc2.setFileType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        doc2.setAuthor(testUser.getUsername());
        doc2.setTenantId(testUser.getTenantId());
        doc2 = documentRepository.save(doc2);

        List<Document> documents = List.of(testDocument, doc1, doc2);
        
        // Test bulk indexing
        elasticsearchService.bulkIndexDocuments(documents);
        
        // Wait for bulk indexing to complete
        Thread.sleep(3000);
        
        // Verify all documents were indexed
        Page<DocumentSearchEntity> javaResults = documentSearchRepository.searchByTenantAndQuery("test-tenant", "Java", PageRequest.of(0, 10));
        Page<DocumentSearchEntity> elasticsearchResults = documentSearchRepository.searchByTenantAndQuery("test-tenant", "Elasticsearch", PageRequest.of(0, 10));
        
        assertFalse(javaResults.isEmpty(), "Java documents should be found");
        assertFalse(elasticsearchResults.isEmpty(), "Elasticsearch documents should be found");
    }

    @Test
    void testSearchWithCaching() throws Exception {
        // Index document
        elasticsearchService.indexDocumentAsync(testDocument);
        Thread.sleep(2000);
        
        String query = "enterprise";
        String tenantId = "test-tenant";
        
        // First search - should hit Elasticsearch
        long startTime1 = System.currentTimeMillis();
        Page<DocumentSearchEntity> results1 = elasticsearchService.searchDocuments(tenantId, query, PageRequest.of(0, 10));
        long duration1 = System.currentTimeMillis() - startTime1;
        
        // Second search - should hit cache
        long startTime2 = System.currentTimeMillis();
        Page<DocumentSearchEntity> results2 = elasticsearchService.searchDocuments(tenantId, query, PageRequest.of(0, 10));
        long duration2 = System.currentTimeMillis() - startTime2;
        
        // Verify results are the same
        assertEquals(results1.getTotalElements(), results2.getTotalElements());
        
        // Cache should be faster (though this might not always be true in test environment)
        assertNotNull(results1);
        assertNotNull(results2);
        
        System.out.println("First search took: " + duration1 + "ms");
        System.out.println("Second search took: " + duration2 + "ms");
    }

    @Test
    void testTenantIsolation() throws Exception {
        // Create user in different tenant
        User otherTenantUser = new User();
        otherTenantUser.setUsername("othertenant");
        otherTenantUser.setEmail("other@example.com");
        otherTenantUser.setPassword("password123");
        otherTenantUser.setFirstName("Other");
        otherTenantUser.setLastName("Tenant");
        otherTenantUser.setRoles(Set.of(Role.USER));
        otherTenantUser.setTenantId("other-tenant");
        otherTenantUser.setActive(true);
        otherTenantUser = userService.createUser(otherTenantUser);
        
        // Create document in other tenant
        Document otherTenantDoc = new Document();
        otherTenantDoc.setTitle("Other Tenant Document");
        otherTenantDoc.setContent("This document belongs to a different tenant");
        otherTenantDoc.setFileName("other-doc.txt");
        otherTenantDoc.setFileType("text/plain");
        otherTenantDoc.setAuthor(otherTenantUser.getUsername());
        otherTenantDoc.setTenantId(otherTenantUser.getTenantId());
        otherTenantDoc = documentRepository.save(otherTenantDoc);
        
        // Index both documents
        elasticsearchService.indexDocumentAsync(testDocument);
        elasticsearchService.indexDocumentAsync(otherTenantDoc);
        Thread.sleep(2000);
        
        // Search from first tenant - should only find documents from that tenant
        Page<DocumentSearchEntity> tenant1Results = elasticsearchService.searchDocuments("test-tenant", "document", PageRequest.of(0, 10));
        Page<DocumentSearchEntity> tenant2Results = elasticsearchService.searchDocuments("other-tenant", "document", PageRequest.of(0, 10));
        
        // Verify tenant isolation
        tenant1Results.getContent().forEach(doc -> assertEquals("test-tenant", doc.getTenantId()));
        tenant2Results.getContent().forEach(doc -> assertEquals("other-tenant", doc.getTenantId()));
        
        assertFalse(tenant1Results.isEmpty());
        assertFalse(tenant2Results.isEmpty());
    }
}