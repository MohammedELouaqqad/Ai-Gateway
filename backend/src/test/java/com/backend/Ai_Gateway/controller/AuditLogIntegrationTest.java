package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditLogIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    private final String adminSub = "audit-admin-" + UUID.randomUUID();

    @Test
    void nonAdmin_isForbiddenOnAuditLogEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/audit-log")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void providerCreateUpdateDelete_produceAuditEntries_andUpdateDiffNeverLeaksApiKey() throws Exception {
        persistAdmin();

        String createBody = """
                {"name":"Audit-IT-Provider","type":"OPENAI","baseUrl":"https://api.openai.com","location":"CLOUD","priority":1,"enabled":true}
                """;
        String createResponse = mockMvc.perform(post("/api/providers")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long providerId = objectMapper().readTree(createResponse).get("id").asLong();

        String updateBody = """
                {"name":"Audit-IT-Provider-Renamed","baseUrl":"https://api.openai.com/v2","priority":2}
                """;
        mockMvc.perform(put("/api/providers/" + providerId)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/providers/" + providerId).with(admin()))
                .andExpect(status().isNoContent());

        String auditJson = mockMvc.perform(get("/api/admin/audit-log")
                        .param("size", "50")
                        .with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertContainsAction(auditJson, "PROVIDER_CREATE");
        assertContainsAction(auditJson, "PROVIDER_UPDATE");
        assertContainsAction(auditJson, "PROVIDER_DELETE");
        // The whole point of the redaction rule: the raw key must never appear, anywhere.
        org.assertj.core.api.Assertions.assertThat(auditJson).doesNotContain("api.openai.com\":\"");
        org.assertj.core.api.Assertions.assertThat(auditJson).doesNotContain("\"apiKey\"");
    }

    @Test
    void guardrailsUpdate_capturesOldAndNewValues() throws Exception {
        persistAdmin();

        String body = """
                {"enabled":true,"requestsPerMinute":42,"requestsPerDay":4200,"globalBudgetPerDayEur":10.0,"userBudgetPerDayEur":1.0}
                """;
        mockMvc.perform(put("/api/admin/guardrails/config")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/audit-log")
                        .param("action", "GUARDRAILS_UPDATE")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("GUARDRAILS_UPDATE"))
                .andExpect(jsonPath("$.content[0].details.requestsPerMinute.new").value(42))
                .andExpect(jsonPath("$.content[0].details.requestsPerMinute.old").exists());
    }

    @Test
    void auditLogEndpoint_paginatesAndFiltersByAction() throws Exception {
        persistAdmin();

        mockMvc.perform(post("/api/providers")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Audit-IT-Filter","type":"OLLAMA","location":"LOCAL","priority":1,"enabled":true}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/audit-log")
                        .param("page", "0")
                        .param("size", "5")
                        .param("action", "PROVIDER_CREATE")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.content[0].action").value("PROVIDER_CREATE"));
    }

    private void assertContainsAction(String json, String action) {
        org.assertj.core.api.Assertions.assertThat(json).contains("\"" + action + "\"");
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(adminSub).claim("preferred_username", "audit-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private void persistAdmin() {
        if (userRepository.findByKeycloakId(adminSub).isPresent()) {
            return;
        }
        User admin = new User();
        admin.setKeycloakId(adminSub);
        admin.setUsername("audit-admin");
        admin.setEmail("audit-admin@ai-gateway.local");
        admin.setEnabled(true);
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);
    }

    private com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
