package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.provider.dto.ProviderModelInfo;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Contrat provider LLM.
 * <p>
 * Dans ce projet, le chemin production (Ollama / OpenAI) passe par
 * {@link LLMProviderRouter#chat} et {@link LLMProviderRouter#stream}
 * (switch sur {@code ProviderType}), pas par une Map de beans.
 * Les implémentations {@code LLMProvider} restent disponibles pour un
 * futur câblage polymorphe (ARC-01).
 */
public interface LLMProvider {
    ChatResponse chat(ChatRequest request);

    /** Flux de chunks ; production via {@link LLMProviderRouter#stream}. */
    Flux<ChatChunk> stream(ChatRequest request);

    List<ProviderModelInfo> models();
}
