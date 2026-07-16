package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.exceptions.ProviderNotImplementedException;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.provider.dto.ollama.OllamaChatRequest;
import com.backend.Ai_Gateway.provider.dto.ollama.OllamaChatResponse;
import com.backend.Ai_Gateway.provider.dto.ollama.OllamaStreamChunkDto;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIChatRequest;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIChatResponse;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIMessage;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIStreamChatRequest;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAiStreamChunkDto;
import com.backend.Ai_Gateway.provider.mapper.OllamaChunkMapper;
import com.backend.Ai_Gateway.provider.mapper.OpenAiChunkMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Point d'entrée production pour Ollama / OpenAI.
 * Routing par {@code switch (provider.getType())} — pas encore de Map&lt;LLMProvider&gt;.
 * {@link #chat} = RestClient sync ; {@link #stream} = WebClient + ChatChunk.
 */
@Service
public class LLMProviderRouter {

    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final String defaultOllamaBaseUrl;
    private final String defaultOpenAiApiKey;

    public LLMProviderRouter(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.ollama.base-url:http://localhost:11434}") String defaultOllamaBaseUrl,
            @Value("${OPENAI_API_KEY:${app.openai.api-key:}}") String defaultOpenAiApiKey
    ) {
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.defaultOllamaBaseUrl = defaultOllamaBaseUrl;
        this.defaultOpenAiApiKey = defaultOpenAiApiKey;
    }

    /** Appel non-streaming (existant, inchangé fonctionnellement). */
    public ChatResponse chat(Provider provider, ChatRequest request) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider is required");
        }
        if (!ProviderCapabilities.hasChatAdapter(provider.getType())) {
            throw new ProviderNotImplementedException(provider.getType());
        }
        return switch (provider.getType()) {
            case OLLAMA -> chatOllama(provider, request);
            case OPENAI, AZURE_OPENAI -> chatOpenAi(provider, request);
            default -> throw new ProviderNotImplementedException(provider.getType());
        };
    }

    /** Appel streaming : Flux de {@link ChatChunk} (aucun merge avant émission). */
    public Flux<ChatChunk> stream(Provider provider, ChatRequest request) {
        if (provider == null) {
            return Flux.error(new IllegalArgumentException("Provider is required"));
        }
        if (!ProviderCapabilities.hasStreamAdapter(provider.getType())) {
            return Flux.error(new ProviderNotImplementedException(provider.getType()));
        }
        return switch (provider.getType()) {
            case OLLAMA -> streamOllama(provider, request);
            // Azure OpenAI uses the same chat-completions SSE protocol as OpenAI
            case OPENAI, AZURE_OPENAI -> streamOpenAi(provider, request);
            default -> Flux.error(new ProviderNotImplementedException(provider.getType()));
        };
    }

    private ChatResponse chatOllama(Provider provider, ChatRequest request) {
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

    private ChatResponse chatOpenAi(Provider provider, ChatRequest request) {
        String apiKey = resolveOpenAiApiKey(provider);
        String baseUrl = resolveBaseUrl(provider.getBaseUrl(), "https://api.openai.com/v1");
        List<OpenAIMessage> messages = request.messages().stream()
                .map(this::toOpenAiMessage)
                .toList();

        RestClient client = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();

        OpenAIChatResponse response = client.post()
                .uri("/chat/completions")
                .body(new OpenAIChatRequest(request.model(), messages))
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

    private Flux<ChatChunk> streamOllama(Provider provider, ChatRequest request) {
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

    private Flux<ChatChunk> streamOpenAi(Provider provider, ChatRequest request) {
        String apiKey = resolveOpenAiApiKey(provider);
        String baseUrl = resolveBaseUrl(provider.getBaseUrl(), "https://api.openai.com/v1");
        List<OpenAIMessage> messages = request.messages().stream()
                .map(this::toOpenAiMessage)
                .toList();

        WebClient client = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();

        // IMPORTANT: do NOT Accept text/event-stream here — Spring's SSE codec would strip
        // "data:" prefixes and our line parser would filter everything out (empty stream).
        // Parse the raw SSE body manually so every OpenAI/Azure model inherits streaming.
        // Buffer lines across DataBuffers: OpenAI events are often larger than one TCP chunk.
        StreamLineBuffer lineBuffer = new StreamLineBuffer();
        return client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.ALL)
                .bodyValue(OpenAIStreamChatRequest.of(request.model(), messages))
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

    private OllamaStreamChunkDto parseOllamaLine(String line) {
        try {
            return objectMapper.readValue(line, OllamaStreamChunkDto.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid Ollama stream chunk: " + line, ex);
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

    private String resolveOpenAiApiKey(Provider provider) {
        if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
            return provider.getApiKey().trim();
        }
        if (defaultOpenAiApiKey != null && !defaultOpenAiApiKey.isBlank()) {
            return defaultOpenAiApiKey.trim();
        }
        throw new IllegalArgumentException(
                "OpenAI API key not configured. Set OPENAI_API_KEY or configure the provider key."
        );
    }

    private String resolveBaseUrl(String providerBaseUrl, String fallback) {
        if (providerBaseUrl != null && !providerBaseUrl.isBlank()) {
            return providerBaseUrl.trim().replaceAll("/$", "");
        }
        return fallback;
    }
}
