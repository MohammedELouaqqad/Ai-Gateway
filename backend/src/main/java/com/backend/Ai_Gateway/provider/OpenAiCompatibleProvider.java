package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.provider.dto.ProviderModelInfo;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIChatRequest;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIChatResponse;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIMessage;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIModelsResponse;
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
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
                .uri(uriBuilder -> buildChatUri(uriBuilder, provider, request))
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
                .uri(uriBuilder -> buildChatUri(uriBuilder, provider, request))
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

    /**
     * {@code GET /models} — forme standard OpenAI, partagée telle quelle par vLLM/SGLang (même
     * shape de réponse que leur {@code /chat/completions}). Azure OpenAI n'a pas cet endpoint
     * dans la même forme (liste par déploiement, API Resource Manager distincte) — laissé de
     * côté délibérément, n'implémente pas {@link ModelListingProvider}, voir sa Javadoc.
     * <p>
     * Protégé, pas exposé directement : chaque famille qui veut l'offrir déclare elle-même
     * {@code implements ModelListingProvider} et délègue ici — permet à Azure de ne pas
     * hériter d'une capacité qu'elle ne supporte pas juste parce qu'elle étend cette classe.
     */
    protected List<ProviderModelInfo> listModelsOpenAiStyle(Provider provider) {
        String baseUrl = resolveBaseUrl(provider.getBaseUrl(), this::defaultBaseUrl);
        RestClient client = restClientBuilder.clone()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> applyAuth(provider, headers))
                .build();

        OpenAIModelsResponse response = client.get()
                .uri("/models")
                .retrieve()
                .body(OpenAIModelsResponse.class);

        if (response == null || response.data() == null) {
            return List.of();
        }
        return response.data().stream()
                .map(m -> new ProviderModelInfo(m.id(), m.id(), m.ownedBy()))
                .toList();
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

    /**
     * Request path, relative to {@code Provider.baseUrl}. Default matches OpenAI/Ollama/vLLM/
     * SGLang, which all speak the same {@code /chat/completions} shape — override only for a
     * family member with a genuinely different path (e.g. Azure's deployment-scoped route).
     */
    protected String chatPath(Provider provider, ChatRequest request) {
        return "/chat/completions";
    }

    /**
     * Extra query params for the chat/stream call. Empty for every family member except Azure,
     * which requires {@code api-version} on every request.
     */
    protected Map<String, String> queryParams(Provider provider, ChatRequest request) {
        return Map.of();
    }

    /**
     * Suffixe auto-complété sur un {@code Provider.baseUrl} personnalisé s'il n'y est pas déjà
     * — évite le piège "j'ai tapé {@code http://host:port} sans {@code /v1}, tout échoue en
     * 404 silencieux" (SSRF/typo humain fréquent, vu en usage réel). {@code "/v1"} pour toute
     * la famille OpenAI-compatible... sauf Azure, qui ne l'utilise jamais (chemin
     * {@code /openai/deployments/{deployment}/...}) — {@link AzureOpenAIProvider} override à
     * {@code ""} pour ne pas en hériter juste parce qu'il étend cette classe.
     */
    protected String baseUrlVersionSuffix() {
        return "/v1";
    }

    private URI buildChatUri(UriBuilder uriBuilder, Provider provider, ChatRequest request) {
        UriBuilder builder = uriBuilder.path(chatPath(provider, request));
        for (Map.Entry<String, String> param : queryParams(provider, request).entrySet()) {
            builder = builder.queryParam(param.getKey(), param.getValue());
        }
        return builder.build();
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
     * <p>
     * Un {@code baseUrl} personnalisé se voit compléter par {@link #baseUrlVersionSuffix()}
     * s'il ne se termine pas déjà par lui — {@code defaultBaseUrl()}, lui, n'est jamais touché
     * (déjà écrit avec le bon suffixe littéralement dans chaque implémentation).
     */
    private String resolveBaseUrl(String providerBaseUrl, Supplier<String> fallback) {
        if (providerBaseUrl != null && !providerBaseUrl.isBlank()) {
            String trimmed = providerBaseUrl.trim().replaceAll("/$", "");
            String suffix = baseUrlVersionSuffix();
            return (suffix.isEmpty() || trimmed.endsWith(suffix)) ? trimmed : trimmed + suffix;
        }
        return fallback.get();
    }
}
