package com.backend.Ai_Gateway.provider.dto;

/**
 * Métadonnée légère d'un modèle exposé par un provider LLM.
 */
public record ProviderModelInfo(
        String id,
        String name,
        String ownedBy
) {}
