package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.provider.dto.ollama.OllamaChatRequest;
import com.backend.Ai_Gateway.provider.dto.ollama.OllamaChatResponse;
import com.backend.Ai_Gateway.provider.dto.ollama.OllamaStreamChunkDto;
import com.backend.Ai_Gateway.provider.mapper.OllamaChunkMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Set;

@Component
public class OllamaProvider implements LLMProvider {

    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final String defaultOllamaBaseUrl;

    public OllamaProvider(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.ollama.base-url:http://localhost:11434}") String defaultOllamaBaseUrl
    ) {
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.defaultOllamaBaseUrl = defaultOllamaBaseUrl;
    }

    @Override
    public Set<ProviderType> types() {
        return Set.of(ProviderType.OLLAMA);
    }

    @Override
    public ChatResponse chat(Provider provider, ChatRequest request) {
        String baseUrl = resolveBaseUrl(provider.getBaseUrl(), defaultOllamaBaseUrl);
        RestClient client = restClientBuilder.baseUrl(baseUrl).build();

        var body = new OllamaChatRequest(request.model(), request.messages(), false);
        OllamaChatResponse response = client.post()
                .uri("/api/chat")
                .body(body)
                .retrieve()
                .body(OllamaChatResponse.class);

        if (response == null || response.message() == null) {
            throw new IllegalStateException("Empty response from Ollama");
        }

        return new ChatResponse(
                response.message().content(),
                response.prompt_eval_count(),
                response.eval_count()
        );
    }

    @Override
    public Flux<ChatChunk> stream(Provider provider, ChatRequest request) {
        String baseUrl = resolveBaseUrl(provider.getBaseUrl(), defaultOllamaBaseUrl);
        WebClient client = webClientBuilder.baseUrl(baseUrl).build();
        var body = new OllamaChatRequest(request.model(), request.messages(), true);

        return client.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> line != null && !line.isBlank())
                .map(this::parseOllamaLine)
                .map(OllamaChunkMapper::toChatChunk)
                .filter(chunk -> chunk.done() || (chunk.content() != null && !chunk.content().isEmpty()));
    }

    private OllamaStreamChunkDto parseOllamaLine(String line) {
        try {
            return objectMapper.readValue(line, OllamaStreamChunkDto.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid Ollama stream chunk: " + line, ex);
        }
    }

    private String resolveBaseUrl(String providerBaseUrl, String fallback) {
        if (providerBaseUrl != null && !providerBaseUrl.isBlank()) {
            return providerBaseUrl.trim().replaceAll("/$", "");
        }
        return fallback;
    }
}
