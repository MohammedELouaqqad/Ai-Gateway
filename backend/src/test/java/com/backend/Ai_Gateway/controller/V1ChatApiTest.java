package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.repositories.ProviderRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.ApiKeyService;
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
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
 * /v1/chat/completions + /v1/models, authenticated by API key (not JWT). LLMProviderRouter
 * is mocked (same pattern as {@link AuthAndChatIntegrationTest} for /api/chat) so these tests
 * exercise real routing/guardrails/usage wiring without a live upstream provider.
 */
class V1ChatApiTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ModelRepository modelRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ApiKeyService apiKeyService;
    @Autowired private UsageGuardService usageGuardService;

    @MockitoBean
    private LLMProviderRouter llmProviderRouter;

    private String rawApiKey;
    private String modelName;
    private Provider provider;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setKeycloakId("v1-it-" + UUID.randomUUID());
        user.setUsername("v1-user");
        user.setEnabled(true);
        user.setCreatedAt(Instant.now());
        user = userRepository.save(user);

        provider = newProvider(ProviderType.OPENAI, "https://example.invalid", 1);
        modelName = "v1-model-" + UUID.randomUUID();
        newModel(modelName, provider);

        rawApiKey = apiKeyService.generate(user.getId(), "ci-key").rawKey();
    }

    @AfterEach
    void resetGuardrails() {
        usageGuardService.resetRuntimeToDefaults();
    }

    @Test
    void sync_returnsOpenAiShape_andRoutesThroughRealProvider() throws Exception {
        when(llmProviderRouter.chat(any(), any())).thenReturn(new ChatResponse("hi there", 5, 2));

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"model":"%s","messages":[{"role":"user","content":"hello"}]}
                                """.formatted(modelName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("chat.completion"))
                .andExpect(jsonPath("$.model").value(modelName))
                .andExpect(jsonPath("$.choices[0].message.content").value("hi there"))
                .andExpect(jsonPath("$.choices[0].finish_reason").value("stop"))
                .andExpect(jsonPath("$.usage.prompt_tokens").value(5))
                .andExpect(jsonPath("$.usage.completion_tokens").value(2));

        verify(llmProviderRouter).chat(argThat(p -> p.getId().equals(provider.getId())), any());
    }

    @Test
    void missingApiKey_401OpenAiShape() throws Exception {
        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"model":"%s","messages":[{"role":"user","content":"hello"}]}
                                """.formatted(modelName)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_api_key"));
    }

    @Test
    void unknownModel_404OpenAiShape() throws Exception {
        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"model":"does-not-exist-%s","messages":[{"role":"user","content":"hi"}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"));
    }

    @Test
    void routesByModelField_notHardcodedToOneProvider() throws Exception {
        Provider providerB = newProvider(ProviderType.OLLAMA, "http://localhost:11434", 2);
        String modelNameB = "v1-model-b-" + UUID.randomUUID();
        newModel(modelNameB, providerB);

        when(llmProviderRouter.chat(any(), any())).thenReturn(new ChatResponse("ok", 1, 1));

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"model":"%s","messages":[{"role":"user","content":"hi"}]}
                                """.formatted(modelNameB)))
                .andExpect(status().isOk());

        verify(llmProviderRouter).chat(argThat(p -> p.getId().equals(providerB.getId())), any());
        verify(llmProviderRouter, never()).chat(argThat(p -> p.getId().equals(provider.getId())), any());
    }

    @Test
    void stream_emitsRawOpenAiSse_noEventLine() throws Exception {
        when(llmProviderRouter.stream(any(), any())).thenReturn(Flux.just(
                ChatChunk.delta("hel"),
                ChatChunk.delta("lo"),
                ChatChunk.completed("stop", ChatChunk.TokenUsage.of(3, 2))
        ));

        MvcResult mvcResult = mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("""
                                {"model":"%s","messages":[{"role":"user","content":"hi"}],"stream":true}
                                """.formatted(modelName)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("\"role\":\"assistant\"")))
                .andExpect(content().string(containsString("hel")))
                .andExpect(content().string(containsString("lo")))
                .andExpect(content().string(containsString("[DONE]")))
                .andExpect(content().string(not(containsString("event:"))));
    }

    @Test
    void modelsList_returnsOnlyUsableModelsInOpenAiShape() throws Exception {
        mockMvc.perform(get("/v1/models").header("Authorization", "Bearer " + rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("list"))
                .andExpect(jsonPath("$.data[?(@.id=='" + modelName + "')]").exists());
    }

    @Test
    void guardrailsApplyToApiKeyTraffic_secondCallWithinTheMinuteIs429() throws Exception {
        mockMvc.perform(put("/api/admin/guardrails/config")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestsPerMinute":1,"requestsPerDay":500}
                                """))
                .andExpect(status().isOk());

        when(llmProviderRouter.chat(any(), any())).thenReturn(new ChatResponse("ok", 1, 1));

        String body = """
                {"model":"%s","messages":[{"role":"user","content":"hi"}]}
                """.formatted(modelName);

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.type").value("rate_limit_error"));
    }

    private Provider newProvider(ProviderType type, String baseUrl, int priority) {
        Provider p = new Provider();
        p.setName("V1-IT-" + type + "-" + UUID.randomUUID());
        p.setType(type);
        p.setLocation(ProviderLocation.LOCAL);
        p.setBaseUrl(baseUrl);
        p.setApiKey("test-key");
        p.setEnabled(true);
        p.setPriority(priority);
        p.setCreatedAt(Instant.now());
        return providerRepository.save(p);
    }

    private Model newModel(String name, Provider provider) {
        Model m = new Model();
        m.setName(name);
        m.setDisplayName(name);
        m.setEnabled(true);
        m.setDefault(false);
        m.setProvider(provider);
        m.setCreatedAt(Instant.now());
        return modelRepository.save(m);
    }
}
