package com.backend.Ai_Gateway.config;

/**
 * @deprecated Prefer {@link com.backend.Ai_Gateway.services.ModelPricingService} (config YAML).
 */
@Deprecated
public final class ModelPricing {

    private ModelPricing() {}

    public record PricePerMillion(double inputUsd, double outputUsd) {}

    private static final PricePerMillion FREE = new PricePerMillion(0, 0);

    public static PricePerMillion forModel(String modelName) {
        return FREE;
    }
}
