package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.config.TestJwtConfig;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiSmokeTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void seedTestUser() {
        // New users are enabled=false by default (admin approval required) — the tests below
        // that resolve "current user" (profile, usage, prompt templates) need this pre-seeded.
        TestJwtConfig.ensureEnabledTestUser(userRepository);
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/models"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listModelsReturnsOk() throws Exception {
        mockMvc.perform(get("/api/models").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void listProvidersReturnsOk() throws Exception {
        mockMvc.perform(get("/api/providers").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void usageSummaryReturnsOk() throws Exception {
        mockMvc.perform(get("/api/usage/summary").with(jwt().jwt(j -> j
                        .subject(TestJwtConfig.TEST_SUBJECT)
                        .claim("preferred_username", "testuser")
                        .claim("email", "test@ai-gateway.local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestCount").exists())
                .andExpect(jsonPath("$.totalTokens").exists());
    }

    @Test
    void createModelWithInvalidBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/models")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"","name":"","providerId":null,"enabled":false,"isDefault":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void listPromptTemplatesReturnsOk() throws Exception {
        mockMvc.perform(get("/api/prompt-templates").with(jwt().jwt(j -> j
                        .subject(TestJwtConfig.TEST_SUBJECT)
                        .claim("preferred_username", "testuser")
                        .claim("email", "test@ai-gateway.local"))))
                .andExpect(status().isOk());
    }

    @Test
    void createPromptTemplateWithInvalidBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/prompt-templates")
                        .with(jwt().jwt(j -> j.subject(TestJwtConfig.TEST_SUBJECT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","description":"","content":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profileReturnsCurrentUser() throws Exception {
        mockMvc.perform(get("/api/profile").with(jwt().jwt(j -> j
                        .subject(TestJwtConfig.TEST_SUBJECT)
                        .claim("preferred_username", "testuser")
                        .claim("email", "test@ai-gateway.local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keycloakId").value(TestJwtConfig.TEST_SUBJECT))
                .andExpect(jsonPath("$.username").value("testuser"));
    }
}
