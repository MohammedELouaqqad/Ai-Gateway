package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.provider.dto.ollama.OllamaChatRequest;
import com.backend.Ai_Gateway.provider.dto.ollama.OllamaChatResponse;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIChatRequest;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIChatResponse;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAIMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class LLMProviderRouter {

    private final RestClient.Builder restClientBuilder;
    private final String defaultOllamaBaseUrl;
    private final String defaultOpenAiApiKey;

    public LLMProviderRouter(
            RestClient.Builder restClientBuilder,
            @Value("${app.ollama.base-url:http://localhost:11434}") String defaultOllamaBaseUrl,
            @Value("${OPENAI_API_KEY:${app.openai.api-key:}}") String defaultOpenAiApiKey
    ) {
        this.restClientBuilder = restClientBuilder;
        this.defaultOllamaBaseUrl = defaultOllamaBaseUrl;
        this.defaultOpenAiApiKey = defaultOpenAiApiKey;
    }

    public ChatResponse chat(Provider provider, ChatRequest request) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider is required");
        }
        return switch (provider.getType()) {
            case OLLAMA -> chatOllama(provider, request);
            case OPENAI -> chatOpenAi(provider, request);
            default -> throw new IllegalArgumentException("Provider type not supported for chat: " + provider.getType());
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
