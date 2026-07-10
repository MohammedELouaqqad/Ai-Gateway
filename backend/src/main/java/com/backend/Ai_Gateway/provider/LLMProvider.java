package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.provider.dto.ProviderModelInfo;

import java.util.List;
import java.util.stream.Stream;

public interface LLMProvider {
    ChatResponse chat(ChatRequest request);

    Stream<ChatMessage> stream(ChatRequest request);

    List<ProviderModelInfo> models();
}
