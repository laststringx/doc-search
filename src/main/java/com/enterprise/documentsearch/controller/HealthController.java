package com.enterprise.documentsearch.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Slf4j
public class HealthController implements HealthIndicator {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        log.info("Health check endpoint called");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Enterprise Document Search");
        response.put("version", "1.0-SNAPSHOT");
        response.put("timestamp", LocalDateTime.now());
        response.put("environment", "development");
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        log.info("Info endpoint called");
        
        Map<String, Object> response = new HashMap<>();
        response.put("application", "Enterprise Document Search");
        response.put("description", "Distributed Document Search Service");
        response.put("version", "1.0-SNAPSHOT");
        response.put("java-version", System.getProperty("java.version"));
        response.put("spring-boot-version", "3.2.0");
        
        return ResponseEntity.ok(response);
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("service", "Enterprise Document Search")
                .withDetail("status", "Running")
                .withDetail("timestamp", LocalDateTime.now())
                .build();
    }
}