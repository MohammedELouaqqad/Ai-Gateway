package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.GuardrailsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelPricingServiceTest {

    private ModelPricingService pricing;

    @BeforeEach
    void setUp() {
        GuardrailsProperties props = new GuardrailsProperties();
        GuardrailsProperties.ModelPrice mini = new GuardrailsProperties.ModelPrice();
        mini.setInputPer1kEur(0.001);
        mini.setOutputPer1kEur(0.002);
        props.getPricing().put("gpt-4o-mini", mini);
        pricing = new ModelPricingService(props);
    }

    @Test
    void unknownModelIsFree() {
        assertThat(pricing.isBillable("llama3.2:1b")).isFalse();
        assertThat(pricing.calculateCostEur(1000, 500, "llama3.2:1b")).isZero();
    }

    @Test
    void pricedModelIsBillable() {
        assertThat(pricing.isBillable("gpt-4o-mini")).isTrue();
        // 1000 in * 0.001 + 500 out * 0.002 = 0.001 + 0.001 = 0.002
        assertThat(pricing.calculateCostEur(1000, 500, "gpt-4o-mini")).isEqualTo(0.002);
    }
}
