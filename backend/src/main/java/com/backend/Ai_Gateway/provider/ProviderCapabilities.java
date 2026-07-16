package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.enums.ProviderType;

/**
 * Indique si CE PROJET a écrit l'adaptateur, PAS si le provider distant
 * en est techniquement capable. SGLang / vLLM streament en réalité via
 * l'API OpenAI-compatible — {@code false} signifie seulement « pas encore câblé ici ».
 */
public final class ProviderCapabilities {

    private ProviderCapabilities() {}

    /** Adaptateur non-streaming ({@link LLMProviderRouter#chat}) présent. */
    public static boolean hasChatAdapter(ProviderType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case OLLAMA, OPENAI, AZURE_OPENAI -> true;
            case SGLANG, VLLM -> false;
        };
    }

    /** Adaptateur streaming ({@link LLMProviderRouter#stream}) présent. */
    public static boolean hasStreamAdapter(ProviderType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case OLLAMA, OPENAI, AZURE_OPENAI -> true;
            case SGLANG, VLLM -> false;
        };
    }
}
