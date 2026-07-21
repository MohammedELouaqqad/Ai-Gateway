package com.backend.Ai_Gateway.config;

import java.util.Map;

/**
 * Prix estimés en USD par million de tokens (input / output).
 * Modèles locaux (Ollama, etc.) = 0.
 */
public final class ModelPricing {

    private ModelPricing() {}

    public record PricePerMillion(double inputUsd, double outputUsd) {}

    private static final PricePerMillion FREE = new PricePerMillion(0, 0);

    private static final Map<String, PricePerMillion> BY_MODEL_NAME = Map.ofEntries(
            Map.entry("llama3.2:1b", FREE),
            Map.entry("meta-llama/Meta-Llama-3-8B-Instruct", FREE),
            Map.entry("mistral-7b-instruct", FREE),
            // OpenAI — tarifs indicatifs (USD / 1M tokens), à ajuster selon la grille officielle
            Map.entry("gpt-4o-mini", new PricePerMillion(0.15, 0.60)),
            Map.entry("gpt-4o", new PricePerMillion(2.50, 10.00)),
            Map.entry("gpt-4o-azure", new PricePerMillion(2.50, 10.00))
    );

    public static PricePerMillion forModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return FREE;
        }
        return BY_MODEL_NAME.getOrDefault(modelName.trim(), FREE);
    }
}
