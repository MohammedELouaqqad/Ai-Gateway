package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.provider.dto.ProviderModelInfo;
import com.backend.Ai_Gateway.provider.dto.ollama.OllamaChatRequest;
import com.backend.Ai_Gateway.provider.dto.ollama.OllamaChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Stream;

@Service
public class OllamaProvider implements LLMProvider {

    private final RestClient restClient;

    public OllamaProvider(RestClient.Builder builder, @Value("${app.ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        var body = new OllamaChatRequest(request.model(), request.messages(), false);
        OllamaChatResponse response = restClient.post()
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
    public Stream<ChatMessage> stream(ChatRequest request) {
        return Stream.empty();
    }

    @Override
    public List<ProviderModelInfo> models() {
        return List.of();
    }
}
