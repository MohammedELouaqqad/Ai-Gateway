package com.backend.Ai_Gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listModelsReturnsOk() throws Exception {
        mockMvc.perform(get("/api/models"))
                .andExpect(status().isOk());
    }

    @Test
    void listProvidersReturnsOk() throws Exception {
        mockMvc.perform(get("/api/providers"))
                .andExpect(status().isOk());
    }

    @Test
    void usageSummaryReturnsOk() throws Exception {
        mockMvc.perform(get("/api/usage/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestCount").exists())
                .andExpect(jsonPath("$.totalTokens").exists());
    }

    @Test
    void createModelWithInvalidBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"","name":"","providerId":null,"enabled":false,"isDefault":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void listPromptTemplatesReturnsOk() throws Exception {
        mockMvc.perform(get("/api/prompt-templates"))
                .andExpect(status().isOk());
    }

    @Test
    void createPromptTemplateWithInvalidBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/prompt-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","description":"","content":""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
