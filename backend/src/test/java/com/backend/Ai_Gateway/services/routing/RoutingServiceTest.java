package com.backend.Ai_Gateway.services.routing;

import com.backend.Ai_Gateway.config.GuardrailsProperties;
import com.backend.Ai_Gateway.config.RoutingProperties;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import com.backend.Ai_Gateway.services.ModelPricingService;
import com.backend.Ai_Gateway.services.UsageGuardService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    @Mock ModelRepository modelRepository;
    @Mock UsageLogRepository usageLogRepository;
    @Mock UsageGuardService usageGuardService;
    @Mock RoutingDecisionLogRepository decisionLogRepository;

    RoutingProperties routingProperties;
    GuardrailsProperties guardrailsProperties;
    ModelPricingService pricingService;
    RoutingService routingService;

    @BeforeEach
    void setUp() {
        routingProperties = new RoutingProperties();
        routingProperties.setEnabled(true);
        routingProperties.setMode(RoutingProperties.Mode.SHADOW);
        routingProperties.setProfile(RoutingProperties.Profile.ECONOMY);

        guardrailsProperties = new GuardrailsProperties();
        GuardrailsProperties.ModelPrice mini = new GuardrailsProperties.ModelPrice();
        mini.setInputPer1kEur(0.001);
        mini.setOutputPer1kEur(0.002);
        guardrailsProperties.getPricing().put("gpt-4o-mini", mini);

        pricingService = new ModelPricingService(guardrailsProperties);
        when(usageGuardService.effectiveConfig()).thenReturn(guardrailsProperties);
        when(usageLogRepository.sumEstimatedCostByUserIdAndCreatedAtBetween(anyLong(), any(), any())).thenReturn(0.0);
        when(usageLogRepository.sumEstimatedCostBetween(any(), any())).thenReturn(0.0);

        routingService = new RoutingService(
                routingProperties,
                modelRepository,
                usageLogRepository,
                pricingService,
                usageGuardService,
                new SimpleMeterRegistry(),
                decisionLogRepository
        );
    }

    @Test
    void filtersDisabledProviderAndRanksFreeFirstForEconomy() {
        Model free = model("llama3.2:1b", true, true, ProviderType.OLLAMA);
        Model paid = model("gpt-4o-mini", true, true, ProviderType.OPENAI);
        Model disabled = model("broken", true, false, ProviderType.OPENAI);

        when(modelRepository.findAll()).thenReturn(List.of(free, paid, disabled));
        when(modelRepository.findByIsDefaultTrue()).thenReturn(Optional.of(free));

        RoutingDecision decision = routingService.decide(1L, 100, false);

        assertThat(decision.eliminated()).extracting(RoutingDecision.EliminatedCandidate::modelName)
                .contains("broken");
        assertThat(decision.eliminated()).anyMatch(e -> e.reason().equals("PROVIDER_INACTIVE"));
        assertThat(decision.ranked()).isNotEmpty();
        assertThat(decision.selectedModelName()).isEqualTo("llama3.2:1b");
        assertThat(decision.ranked().get(0).totalScore())
                .isGreaterThanOrEqualTo(decision.ranked().get(decision.ranked().size() - 1).totalScore());
    }

    @Test
    void eliminatesWhenContextTooSmall() {
        routingProperties.setDefaultContextChars(50);
        routingService = new RoutingService(
                routingProperties,
                modelRepository,
                usageLogRepository,
                pricingService,
                usageGuardService,
                new SimpleMeterRegistry(),
                decisionLogRepository
        );

        Model free = model("llama3.2:1b", true, true, ProviderType.OLLAMA);
        when(modelRepository.findAll()).thenReturn(List.of(free));
        when(modelRepository.findByIsDefaultTrue()).thenReturn(Optional.of(free));

        RoutingDecision decision = routingService.decide(1L, 500, false);

        assertThat(decision.ranked()).isEmpty();
        assertThat(decision.eliminated()).anyMatch(e -> e.reason().startsWith("CONTEXT_TOO_SMALL"));
        assertThat(decision.hasWinner()).isFalse();
    }

    @Test
    void qualityProfile_prefersHigherQualityScore() {
        routingProperties.setProfile(RoutingProperties.Profile.QUALITY);
        routingProperties.getModelScores().put("llama3.2:1b", score(35, 95));
        routingProperties.getModelScores().put("gpt-4o-mini", score(95, 55));
        GuardrailsProperties.ModelPrice mini = new GuardrailsProperties.ModelPrice();
        mini.setInputPer1kEur(0.001);
        mini.setOutputPer1kEur(0.002);
        guardrailsProperties.getPricing().put("gpt-4o-mini", mini);
        pricingService = new ModelPricingService(guardrailsProperties);
        routingService = new RoutingService(
                routingProperties,
                modelRepository,
                usageLogRepository,
                pricingService,
                usageGuardService,
                new SimpleMeterRegistry(),
                decisionLogRepository
        );

        Model free = model("llama3.2:1b", true, true, ProviderType.OLLAMA);
        Model paid = model("gpt-4o-mini", true, true, ProviderType.OPENAI);
        when(modelRepository.findAll()).thenReturn(List.of(free, paid));
        when(modelRepository.findByIsDefaultTrue()).thenReturn(Optional.of(free));

        RoutingDecision decision = routingService.decide(1L, 100, false);

        assertThat(decision.profile()).isEqualTo(RoutingProperties.Profile.QUALITY);
        assertThat(decision.selectedModelName()).isEqualTo("gpt-4o-mini");
        assertThat(decision.mode()).isEqualTo(RoutingProperties.Mode.SHADOW);
    }

    @Test
    void speedProfile_prefersFasterLocalModel() {
        routingProperties.setProfile(RoutingProperties.Profile.SPEED);
        routingProperties.getModelScores().put("llama3.2:1b", score(35, 95));
        routingProperties.getModelScores().put("gpt-4o-mini", score(75, 40));
        GuardrailsProperties.ModelPrice mini = new GuardrailsProperties.ModelPrice();
        mini.setInputPer1kEur(0.001);
        mini.setOutputPer1kEur(0.002);
        guardrailsProperties.getPricing().put("gpt-4o-mini", mini);
        pricingService = new ModelPricingService(guardrailsProperties);
        routingService = new RoutingService(
                routingProperties,
                modelRepository,
                usageLogRepository,
                pricingService,
                usageGuardService,
                new SimpleMeterRegistry(),
                decisionLogRepository
        );

        Model free = model("llama3.2:1b", true, true, ProviderType.OLLAMA);
        Model paid = model("gpt-4o-mini", true, true, ProviderType.OPENAI);
        when(modelRepository.findAll()).thenReturn(List.of(free, paid));
        when(modelRepository.findByIsDefaultTrue()).thenReturn(Optional.of(free));

        RoutingDecision decision = routingService.decide(1L, 100, false);

        assertThat(decision.profile()).isEqualTo(RoutingProperties.Profile.SPEED);
        assertThat(decision.selectedModelName()).isEqualTo("llama3.2:1b");
    }

    @Test
    void activeMode_isPropagatedOnDecision() {
        routingProperties.setMode(RoutingProperties.Mode.ACTIVE);
        routingService = new RoutingService(
                routingProperties,
                modelRepository,
                usageLogRepository,
                pricingService,
                usageGuardService,
                new SimpleMeterRegistry(),
                decisionLogRepository
        );
        Model free = model("llama3.2:1b", true, true, ProviderType.OLLAMA);
        when(modelRepository.findAll()).thenReturn(List.of(free));
        when(modelRepository.findByIsDefaultTrue()).thenReturn(Optional.of(free));

        RoutingDecision decision = routingService.decide(1L, 50, false);
        assertThat(decision.mode()).isEqualTo(RoutingProperties.Mode.ACTIVE);
        assertThat(decision.hasWinner()).isTrue();
    }

    private static RoutingProperties.ModelScore score(double quality, double speed) {
        RoutingProperties.ModelScore ms = new RoutingProperties.ModelScore();
        ms.setQuality(quality);
        ms.setSpeed(speed);
        return ms;
    }

    private static Model model(String name, boolean modelEnabled, boolean providerEnabled, ProviderType type) {
        Provider provider = new Provider();
        provider.setId(name.hashCode() + 100L);
        provider.setName(name + "-provider");
        provider.setType(type);
        provider.setLocation(type == ProviderType.OLLAMA ? ProviderLocation.LOCAL : ProviderLocation.CLOUD);
        provider.setEnabled(providerEnabled);

        Model model = new Model();
        model.setId((long) Math.abs(name.hashCode()));
        model.setName(name);
        model.setDisplayName(name);
        model.setEnabled(modelEnabled);
        model.setProvider(provider);
        return model;
    }
}
