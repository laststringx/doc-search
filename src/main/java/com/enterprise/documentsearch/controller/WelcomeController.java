package com.enterprise.documentsearch.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/")
@Slf4j
public class WelcomeController {

    @GetMapping
    public Map<String, Object> welcome() {
        log.info("Welcome endpoint called");
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Welcome to Enterprise Document Search Service");
        response.put("status", "Active");
        response.put("documentation", "/api/v1/info");
        response.put("health", "/api/v1/health");
        
        return response;
    }
}