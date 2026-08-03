package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.Set;

/**
 * Adaptateur LLM par famille de protocole (Ollama natif, OpenAI-compatible, ...).
 */
public interface LLMProvider {

    /** Types pris en charge par ce bean (un bean peut couvrir plusieurs types). */
    Set<ProviderType> types();

    ChatResponse chat(Provider provider, ChatRequest request);

    Flux<ChatChunk> stream(Provider provider, ChatRequest request);
}
