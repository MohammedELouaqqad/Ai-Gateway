package com.backend.Ai_Gateway.services.routing;

import com.backend.Ai_Gateway.config.RoutingProperties;
import com.backend.Ai_Gateway.entity.Model;

import java.util.List;
import java.util.Map;

/** Décision pure — n'appelle jamais un LLM. */
public record RoutingDecision(
        RoutingProperties.Mode mode,
        RoutingProperties.Profile profile,
        String selectedModelName,
        Long selectedModelId,
        String fallbackModelName,
        double selectedEstimatedCostEur,
        double fallbackEstimatedCostEur,
        int promptChars,
        List<RankedCandidate> ranked,
        List<EliminatedCandidate> eliminated,
        Map<String, Object> signals
) {
    public record RankedCandidate(
            Long modelId,
            String modelName,
            String providerName,
            double estimatedCostEur,
            double costScore,
            double qualityScore,
            double speedScore,
            double totalScore,
            Map<String, Double> scoreDetail
    ) {}

    public record EliminatedCandidate(
            Long modelId,
            String modelName,
            String providerName,
            String reason
    ) {}

    public boolean hasWinner() {
        return selectedModelName != null && !selectedModelName.isBlank();
    }

    public static RoutingDecision empty(
            RoutingProperties.Mode mode,
            RoutingProperties.Profile profile,
            String fallbackModelName,
            int promptChars,
            Map<String, Object> signals,
            List<EliminatedCandidate> eliminated
    ) {
        return new RoutingDecision(
                mode,
                profile,
                null,
                null,
                fallbackModelName,
                0,
                0,
                promptChars,
                List.of(),
                eliminated,
                signals
        );
    }
}
