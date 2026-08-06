package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.repositories.ProviderRepository;
import com.backend.Ai_Gateway.services.UsageGuardService;
import com.backend.Ai_Gateway.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auth RBAC, chat happy path, RPM 429, and SSE stream — mocked LLM.
 */
class AuthAndChatIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ModelRepository modelRepository;

    @Autowired
    private UsageGuardService usageGuardService;

    @MockitoBean
    private LLMProviderRouter llmProviderRouter;

    private String modelName;

    @BeforeEach
    void ensureModel() {
        modelName = "it-model-" + UUID.randomUUID();
        Provider provider = new Provider();
        provider.setName("IT-Ollama-" + UUID.randomUUID());
        provider.setType(ProviderType.OLLAMA);
        provider.setLocation(ProviderLocation.LOCAL);
        provider.setBaseUrl("http://localhost:11434");
        provider.setEnabled(true);
        provider.setPriority(1);
        provider.setCreatedAt(Instant.now());
        provider = providerRepository.save(provider);

        Model model = new Model();
        model.setName(modelName);
        model.setDisplayName("IT Model");
        model.setEnabled(true);
        model.setDefault(false);
        model.setProvider(provider);
        model.setCreatedAt(Instant.now());
        modelRepository.save(model);
    }

    @AfterEach
    void resetGuardrails() {
        usageGuardService.resetRuntimeToDefaults();
    }

    @Test
    void unauthenticated_api_returns401() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotAccessAdminGuardrails() throws Exception {
        mockMvc.perform(get("/api/admin/guardrails/config")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void chatWithExplicitModel_returnsAssistantContent() throws Exception {
        when(llmProviderRouter.chat(any(), any()))
                .thenReturn(new ChatResponse("bonjour-test", 3, 2));

        mockMvc.perform(post("/api/chat")
                        .with(jwt()
                                .jwt(j -> j
                                        .subject("chat-it-" + UUID.randomUUID())
                                        .claim("preferred_username", "chatuser")
                                        .claim("email", "chat@ai-gateway.local"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"salut","model":"%s","stream":false}
                                """.formatted(modelName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("bonjour-test"));
    }

    @Test
    void chatExceedingRpm_returns429Json() throws Exception {
        mockMvc.perform(put("/api/admin/guardrails/config")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestsPerMinute":1,"requestsPerDay":500}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestsPerMinute").value(1));

        when(llmProviderRouter.chat(any(), any()))
                .thenReturn(new ChatResponse("ok", 1, 1));

        String subject = "rpm-it-" + UUID.randomUUID();
        var userJwt = jwt()
                .jwt(j -> j
                        .subject(subject)
                        .claim("preferred_username", "rpmuser")
                        .claim("email", "rpm@ai-gateway.local"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));

        mockMvc.perform(post("/api/chat")
                        .with(userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"first","model":"%s","stream":false}
                                """.formatted(modelName)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/chat")
                        .with(userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"second","model":"%s","stream":false}
                                """.formatted(modelName)))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.reason").value("rate_limit_minute"));
    }

    @Test
    void chatStream_happyPath_emitsSseChunks() throws Exception {
        when(llmProviderRouter.stream(any(), any())).thenReturn(Flux.just(
                ChatChunk.delta("hello"),
                ChatChunk.completed("stop", ChatChunk.TokenUsage.of(2, 1))
        ));

        MvcResult mvcResult = mockMvc.perform(post("/api/chat/stream")
                        .with(jwt()
                                .jwt(j -> j
                                        .subject("stream-it-" + UUID.randomUUID())
                                        .claim("preferred_username", "streamuser")
                                        .claim("email", "stream@ai-gateway.local"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"ping","model":"%s","stream":true}
                                """.formatted(modelName)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:chunk")))
                .andExpect(content().string(containsString("hello")));
    }
}
