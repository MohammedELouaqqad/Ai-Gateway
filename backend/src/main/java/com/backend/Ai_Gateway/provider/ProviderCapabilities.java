package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.enums.ProviderType;

/**
 * Capacités héritées par tous les modèles d'un provider type.
 * Ajouter un modèle n'exige pas de nouveau DTO/mapper — seul un nouveau
 * {@link ProviderType} nécessite du code développeur.
 */
public final class ProviderCapabilities {

    private ProviderCapabilities() {}

    public static boolean supportsChat(ProviderType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case OLLAMA, OPENAI, AZURE_OPENAI -> true;
            case SGLANG, VLLM -> false;
        };
    }

    /** Stream disponible dès qu'un adaptateur provider existe (héritage modèles). */
    public static boolean supportsStreaming(ProviderType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case OLLAMA, OPENAI, AZURE_OPENAI -> true;
            case SGLANG, VLLM -> false;
        };
    }
}
