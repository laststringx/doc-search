package com.enterprise.documentsearch.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
public class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldReturnHealthStatus() throws Exception {
        this.mockMvc.perform(get("/api/v1/health"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("Enterprise Document Search"))
                .andExpect(jsonPath("$.version").value("1.0-SNAPSHOT"));
    }

    @Test
    public void shouldReturnInfo() throws Exception {
        this.mockMvc.perform(get("/api/v1/info"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("Enterprise Document Search"))
                .andExpect(jsonPath("$.description").value("Distributed Document Search Service"));
    }
}