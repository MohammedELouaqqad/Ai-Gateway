package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.provider.dto.ProviderModelInfo;
import reactor.core.publisher.Flux;

import java.util.List;

/** Stub — chat/stream production via {@link LLMProviderRouter}. */
public class OpenAIProvider implements LLMProvider {
    @Override
    public ChatResponse chat(ChatRequest request) {
        return null;
    }

    @Override
    public Flux<ChatChunk> stream(ChatRequest request) {
        return Flux.empty();
    }

    @Override
    public List<ProviderModelInfo> models() {
        return List.of();
    }
}
