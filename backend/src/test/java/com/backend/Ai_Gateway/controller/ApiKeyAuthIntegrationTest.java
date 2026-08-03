package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.config.TestJwtConfig;
import com.backend.Ai_Gateway.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the {@code /v1/**} API-key filter chain end-to-end. {@code /v1/ping} is
 * test-only scaffolding (registered below) standing in for the real OpenAI-compatible
 * endpoints that land in the next phase — this test only proves the auth boundary.
 */
class ApiKeyAuthIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class V1PingTestConfig {
        @Bean
        V1PingController v1PingController() {
            return new V1PingController();
        }
    }

    @RestController
    static class V1PingController {
        @GetMapping("/v1/ping")
        String ping() {
            return "pong";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void missingKeyIsRejected() throws Exception {
        mockMvc.perform(get("/v1/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_api_key"));
    }

    @Test
    void garbageKeyIsRejected() throws Exception {
        mockMvc.perform(get("/v1/ping").header("Authorization", "Bearer sk-not-a-real-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_api_key"));
    }

    @Test
    void validKeyPassesThrough() throws Exception {
        String rawKey = createApiKeyAndGetRawKey();

        mockMvc.perform(get("/v1/ping").header("Authorization", "Bearer " + rawKey))
                .andExpect(status().isOk());
    }

    @Test
    void revokedKeyIsRejected() throws Exception {
        String responseJson = mockMvc.perform(post("/api/api-keys")
                        .with(jwt().jwt(j -> j
                                .subject(TestJwtConfig.TEST_SUBJECT)
                                .claim("preferred_username", "testuser")
                                .claim("email", "test@ai-gateway.local")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"to-revoke\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var node = objectMapper.readTree(responseJson);
        long id = node.get("id").asLong();
        String rawKey = node.get("rawKey").asText();

        mockMvc.perform(patch("/api/api-keys/" + id + "/revoke")
                        .with(jwt().jwt(j -> j
                                .subject(TestJwtConfig.TEST_SUBJECT)
                                .claim("preferred_username", "testuser")
                                .claim("email", "test@ai-gateway.local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/v1/ping").header("Authorization", "Bearer " + rawKey))
                .andExpect(status().isUnauthorized());
    }

    private String createApiKeyAndGetRawKey() throws Exception {
        String responseJson = mockMvc.perform(post("/api/api-keys")
                        .with(jwt().jwt(j -> j
                                .subject(TestJwtConfig.TEST_SUBJECT)
                                .claim("preferred_username", "testuser")
                                .claim("email", "test@ai-gateway.local")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ci-key\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(responseJson).get("rawKey").asText();
    }
}
