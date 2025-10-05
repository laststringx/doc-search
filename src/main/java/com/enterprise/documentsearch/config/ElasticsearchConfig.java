package com.enterprise.documentsearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import java.time.Duration;

/**
 * Elasticsearch configuration optimized for enterprise-scale
 * document search with 10M+ documents and sub-second response times.
 * Disabled for test profile.
 */
@Configuration
@Profile("!test")
@EnableElasticsearchRepositories(basePackages = "com.enterprise.documentsearch.repository")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUris;

    @Value("${spring.elasticsearch.username:}")
    private String username;

    @Value("${spring.elasticsearch.password:}")
    private String password;

    @Value("${spring.elasticsearch.connection-timeout:15s}")
    private Duration connectionTimeout;

    @Value("${spring.elasticsearch.socket-timeout:60s}")
    private Duration socketTimeout;

    @Override
    public ClientConfiguration clientConfiguration() {
        String host = elasticsearchUris.replace("http://", "").replace("https://", "");
        
        if (username != null && !username.isEmpty()) {
            return ClientConfiguration.builder()
                    .connectedTo(host)
                    .withConnectTimeout(connectionTimeout)
                    .withSocketTimeout(socketTimeout)
                    .withBasicAuth(username, password)
                    .build();
        } else {
            return ClientConfiguration.builder()
                    .connectedTo(host)
                    .withConnectTimeout(connectionTimeout)
                    .withSocketTimeout(socketTimeout)
                    .build();
        }
    }
}