package com.enterprise.documentsearch.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Simple integration test to verify Elasticsearch components are properly configured
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.elasticsearch.uris=http://localhost:9200",
    "management.endpoints.web.exposure.include=health,metrics,prometheus",
    "spring.data.elasticsearch.repositories.enabled=true"
})
class ElasticsearchConfigurationTest {

    @Test
    void contextLoads() {
        // This test passes if the application context loads successfully
        // with all Elasticsearch components properly configured
    }
}