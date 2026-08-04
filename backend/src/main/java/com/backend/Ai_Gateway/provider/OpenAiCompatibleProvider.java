package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIChatRequest;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIChatResponse;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIMessage;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIStreamChatRequest;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAiStreamChunkDto;
import com.backend.Ai_Gateway.provider.mapper.OpenAiChunkMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

/**
 * Shared OpenAI chat/completions + SSE implementation.
 * Supports OPENAI, AZURE_OPENAI, and future OpenAI-compatible backends. Per-provider
 * differences (auth scheme, base URL, model reference) are extension points below —
 * a subclass must never re-implement {@link #chat} / {@link #stream} itself.
 */
public abstract class OpenAiCompatibleProvider implements LLMProvider {

    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    protected OpenAiCompatibleProvider(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatResponse chat(Provider provider, ChatRequest request) {
        String baseUrl = resolveBaseUrl(provider.getBaseUrl(), this::defaultBaseUrl);
        List<OpenAIMessage> messages = request.messages().stream()
                .map(this::toOpenAiMessage)
                .toList();

        RestClient client = restClientBuilder.clone()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> applyAuth(provider, headers))
                .build();

        OpenAIChatResponse response = client.post()
                .uri("/chat/completions")
                .body(new OpenAIChatRequest(resolveModelRef(provider, request), messages))
                .retrieve()
                .body(OpenAIChatResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("Empty response from OpenAI");
        }

        OpenAIMessage assistantMessage = response.choices().get(0).message();
        if (assistantMessage == null || assistantMessage.content() == null) {
            throw new IllegalStateException("Empty assistant message from OpenAI");
        }

        Integer promptTokens = response.usage() != null ? response.usage().promptTokens() : 0;
        Integer completionTokens = response.usage() != null ? response.usage().completionTokens() : 0;

        return new ChatResponse(assistantMessage.content(), promptTokens, completionTokens);
    }

    @Override
    public Flux<ChatChunk> stream(Provider provider, ChatRequest request) {
        String baseUrl = resolveBaseUrl(provider.getBaseUrl(), this::defaultBaseUrl);
        List<OpenAIMessage> messages = request.messages().stream()
                .map(this::toOpenAiMessage)
                .toList();

        WebClient client = webClientBuilder.clone()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> applyAuth(provider, headers))
                .build();

        // IMPORTANT: do NOT Accept text/event-stream here — Spring's SSE codec would strip
        // "data:" prefixes and our line parser would filter everything out (empty stream).
        // Parse the raw SSE body manually so every OpenAI-compatible model inherits streaming.
        // Buffer lines across DataBuffers: events are often larger than one TCP chunk.
        StreamLineBuffer lineBuffer = new StreamLineBuffer();
        return client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.ALL)
                .bodyValue(OpenAIStreamChatRequest.of(resolveModelRef(provider, request), messages))
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .map(this::dataBufferToString)
                .concatMapIterable(lineBuffer::appendAndDrain)
                .concatWith(Flux.defer(() -> Flux.fromIterable(lineBuffer.flush())))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .takeUntil("[DONE]"::equals)
                .filter(data -> !"[DONE]".equals(data))
                .map(this::parseOpenAiData)
                .map(OpenAiChunkMapper::toChatChunk)
                .filter(chunk -> chunk.done() || (chunk.content() != null && !chunk.content().isEmpty()));
    }

    /** Only called when {@code Provider.baseUrl} is unset — may throw if this family has no sane default. */
    protected abstract String defaultBaseUrl();

    protected abstract String resolveApiKey(Provider provider);

    /** Sets whatever auth scheme this family member uses (Bearer, {@code api-key}, none…). */
    protected abstract void applyAuth(Provider provider, HttpHeaders headers);

    /**
     * Model identifier sent in the request body. Default = identity (the {@code Model.name}
     * row as-is). Override only if a family member needs a different mapping.
     */
    protected String resolveModelRef(Provider provider, ChatRequest request) {
        return request.model();
    }

    private String dataBufferToString(DataBuffer dataBuffer) {
        try {
            int size = dataBuffer.readableByteCount();
            byte[] bytes = new byte[size];
            dataBuffer.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(dataBuffer);
        }
    }

    private OpenAiStreamChunkDto parseOpenAiData(String data) {
        try {
            return objectMapper.readValue(data, OpenAiStreamChunkDto.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid OpenAI stream chunk: " + data, ex);
        }
    }

    private OpenAIMessage toOpenAiMessage(ChatMessage message) {
        String role = switch (message.role()) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
        };
        return new OpenAIMessage(role, message.content());
    }

    /**
     * {@code fallback} is a {@link Supplier}, not a plain value: some family members
     * (e.g. Azure) have no sane default and throw from {@link #defaultBaseUrl()} — it must
     * only be evaluated when {@code Provider.baseUrl} is actually missing.
     */
    private String resolveBaseUrl(String providerBaseUrl, Supplier<String> fallback) {
        if (providerBaseUrl != null && !providerBaseUrl.isBlank()) {
            return providerBaseUrl.trim().replaceAll("/$", "");
        }
        return fallback.get();
    }
}
