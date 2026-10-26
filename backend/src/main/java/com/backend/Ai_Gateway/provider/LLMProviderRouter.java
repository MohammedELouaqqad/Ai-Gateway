package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.exceptions.ProviderNotImplementedException;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Production entry point for chat + stream provider calls.
 * Delegates to the provider registry (no switch by ProviderType).
 */
@Service
public class LLMProviderRouter {

    private final ProviderRegistry registry;

    public LLMProviderRouter(ProviderRegistry registry) {
        this.registry = registry;
    }

    public ChatResponse chat(Provider provider, ChatRequest request) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider is required");
        }
        if (!registry.supports(provider.getType())) {
            throw new ProviderNotImplementedException(provider.getType());
        }
        return registry.get(provider.getType()).chat(provider, request);
    }

    public Flux<ChatChunk> stream(Provider provider, ChatRequest request) {
        if (provider == null) {
            return Flux.error(new IllegalArgumentException("Provider is required"));
        }
        if (!registry.supports(provider.getType())) {
            return Flux.error(new ProviderNotImplementedException(provider.getType()));
        }
        return registry.get(provider.getType()).stream(provider, request);
    }
}
