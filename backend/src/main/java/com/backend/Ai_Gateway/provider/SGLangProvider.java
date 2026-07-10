package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.provider.dto.ProviderModelInfo;

import java.util.List;
import java.util.stream.Stream;

public class SGLangProvider implements LLMProvider {
    @Override
    public ChatResponse chat(ChatRequest request) {
        return null;
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
