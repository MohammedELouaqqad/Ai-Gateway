package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.GuardrailsProperties;
import org.springframework.stereotype.Service;

/**
 * Prix EUR / 1K tokens depuis {@code app.guardrails.pricing}.
 * Modèle absent de la table = gratuit (Ollama, etc.).
 */
@Service
public class ModelPricingService {

    public record PricePer1k(double inputEur, double outputEur) {
        public boolean isBillable() {
            return inputEur > 0 || outputEur > 0;
        }
    }

    private static final PricePer1k FREE = new PricePer1k(0, 0);

    private final GuardrailsProperties properties;

    public ModelPricingService(GuardrailsProperties properties) {
        this.properties = properties;
    }

    public PricePer1k forModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return FREE;
        }
        GuardrailsProperties.ModelPrice p = properties.getPricing().get(modelName.trim());
        if (p == null) {
            return FREE;
        }
        return new PricePer1k(p.getInputPer1kEur(), p.getOutputPer1kEur());
    }

    public double calculateCostEur(int promptTokens, int completionTokens, String modelName) {
        PricePer1k price = forModel(modelName);
        return (promptTokens / 1000.0) * price.inputEur()
                + (completionTokens / 1000.0) * price.outputEur();
    }

    public boolean isBillable(String modelName) {
        return forModel(modelName).isBillable();
    }
}
