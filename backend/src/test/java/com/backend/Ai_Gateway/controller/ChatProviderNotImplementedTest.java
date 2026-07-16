package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.config.TestJwtConfig;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.repositories.ProviderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtConfig.class)
class ChatProviderNotImplementedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ModelRepository modelRepository;

    @Test
    void streamWithoutAdapterReturns501JsonNotSse() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Provider provider = new Provider();
        provider.setName("SGLang-QA-" + suffix);
        provider.setType(ProviderType.SGLANG);
        provider.setLocation(ProviderLocation.LOCAL);
        provider.setBaseUrl("http://localhost:30000");
        provider.setEnabled(true);
        provider.setPriority(99);
        provider.setCreatedAt(Instant.now());
        provider = providerRepository.save(provider);

        String modelName = "sglang-stub-" + suffix;
        Model model = new Model();
        model.setDisplayName("SGLang Stub");
        model.setName(modelName);
        model.setEnabled(true);
        model.setDefault(false);
        model.setCreatedAt(Instant.now());
        model.setProvider(provider);
        modelRepository.save(model);

        MvcResult result = mockMvc.perform(post("/api/chat/stream")
                        .with(jwt().jwt(j -> j
                                .subject(TestJwtConfig.TEST_SUBJECT)
                                .claim("preferred_username", "testuser")
                                .claim("email", "test@ai-gateway.local")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"bonjour","model":"%s","stream":true}
                                """.formatted(modelName)))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.status").value(501))
                .andExpect(jsonPath("$.message", containsString("SGLANG")))
                .andExpect(jsonPath("$.message", containsString("adaptateur manquant")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(not(containsString("event:"))))
                .andReturn();

        String contentType = result.getResponse().getContentType();
        org.junit.jupiter.api.Assertions.assertNotNull(contentType);
        org.junit.jupiter.api.Assertions.assertFalse(
                contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE),
                "SseEmitter must not be opened for unimplemented providers"
        );
    }

    @Test
    void syncChatWithoutAdapterReturns501() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Provider provider = new Provider();
        provider.setName("vLLM-QA-" + suffix);
        provider.setType(ProviderType.VLLM);
        provider.setLocation(ProviderLocation.LOCAL);
        provider.setBaseUrl("http://localhost:8000");
        provider.setEnabled(true);
        provider.setPriority(99);
        provider.setCreatedAt(Instant.now());
        provider = providerRepository.save(provider);

        String modelName = "vllm-stub-" + suffix;
        Model model = new Model();
        model.setDisplayName("vLLM Stub");
        model.setName(modelName);
        model.setEnabled(true);
        model.setDefault(false);
        model.setCreatedAt(Instant.now());
        model.setProvider(provider);
        modelRepository.save(model);

        mockMvc.perform(post("/api/chat")
                        .with(jwt().jwt(j -> j
                                .subject(TestJwtConfig.TEST_SUBJECT)
                                .claim("preferred_username", "testuser")
                                .claim("email", "test@ai-gateway.local")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"bonjour","model":"%s","stream":false}
                                """.formatted(modelName)))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.status").value(501))
                .andExpect(jsonPath("$.message", containsString("VLLM")));
    }
}
