package com.backend.Ai_Gateway.services.routing;

import com.backend.Ai_Gateway.config.GuardrailsProperties;
import com.backend.Ai_Gateway.config.RoutingProperties;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingActivationCorrectionsTest {

    @Mock ModelRepository modelRepository;
    @Mock UsageLogRepository usageLogRepository;
    @Mock UsageGuardService usageGuardService;
    @Mock RoutingDecisionLogRepository decisionLogRepository;

    RoutingProperties routingProperties;
    RoutingService routingService;

    @BeforeEach
    void setUp() {
        routingProperties = new RoutingProperties();
        routingProperties.setEnabled(true);
        routingProperties.setMode(RoutingProperties.Mode.CANARY);
        routingProperties.setCanaryPercent(25);

        GuardrailsProperties guardrails = new GuardrailsProperties();
        lenient().when(usageGuardService.effectiveConfig()).thenReturn(guardrails);

        routingService = new RoutingService(
                routingProperties,
                modelRepository,
                usageLogRepository,
                new ModelPricingService(guardrails),
                usageGuardService,
                new SimpleMeterRegistry(),
                decisionLogRepository
        );
    }

    @Test
    void canary_isStickyByConversation_notPerRequest() {
        boolean a1 = routingService.shouldApplyLiveWinner(42L, 1L);
        boolean a2 = routingService.shouldApplyLiveWinner(42L, 1L);
        assertThat(a1).isEqualTo(a2);
    }

    @Test
    void canaryZero_neverLive_activeAlwaysLive() {
        routingProperties.setCanaryPercent(0);
        routingService.updateRuntimeConfig(null, "CANARY", null, null, null, 0);
        assertThat(routingService.shouldApplyLiveWinner(7L, 1L)).isFalse();

        routingService.updateRuntimeConfig(null, "ACTIVE", null, null, null, null);
        assertThat(routingService.shouldApplyLiveWinner(7L, 1L)).isTrue();
    }

    @Test
    void persistAndMeter_storesPromptAndMessageLinks_forJudge() {
        RoutingDecision decision = new RoutingDecision(
                RoutingProperties.Mode.SHADOW,
                RoutingProperties.Profile.BALANCED,
                "gpt-4o-mini",
                2L,
                "llama3.2:1b",
                0.01,
                0,
                12,
                List.of(),
                List.of(),
                Map.of()
        );
        when(decisionLogRepository.save(any())).thenAnswer(inv -> {
            RoutingDecisionLog log = inv.getArgument(0);
            log.setId(55L);
            return log;
        });

        Long id = routingService.persistAndMeter(
                decision, 1L, "llama3.2:1b", true, "hello world", 9L, 88L);

        assertThat(id).isEqualTo(55L);
        verify(decisionLogRepository).save(org.mockito.ArgumentMatchers.argThat(log ->
                "hello world".equals(log.getPromptSnapshot())
                        && Long.valueOf(9L).equals(log.getConversationId())
                        && Long.valueOf(88L).equals(log.getUserMessageId())
                        && log.isDiverged()
        ));
    }

    @Test
    void attachAppliedResponse_storesSnapshot_notReRunAppliedModel() {
        RoutingDecisionLog existing = new RoutingDecisionLog();
        existing.setId(55L);
        existing.setDiverged(true);
        existing.setAppliedModelName("llama3.2:1b");
        existing.setSelectedModelName("gpt-4o-mini");
        when(decisionLogRepository.findById(55L)).thenReturn(java.util.Optional.of(existing));
        when(decisionLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        routingService.attachAppliedResponse(55L, 100L, "assistant answer", 5, 10);

        assertThat(existing.getAppliedAssistantMessageId()).isEqualTo(100L);
        assertThat(existing.getAppliedResponseSnapshot()).isEqualTo("assistant answer");
        assertThat(existing.getAppliedActualCostEur()).isNotNull();
        // Diverged SHADOW: proposed cost stays unset until judge
        assertThat(existing.getProposedActualCostEur()).isNull();
    }

    @Test
    void attachAppliedResponse_liveWinner_recordsProposedActualCost() {
        RoutingDecisionLog existing = new RoutingDecisionLog();
        existing.setId(56L);
        existing.setAppliedModelName("gpt-4o-mini");
        existing.setSelectedModelName("gpt-4o-mini");
        when(decisionLogRepository.findById(56L)).thenReturn(java.util.Optional.of(existing));
        when(decisionLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        routingService.attachAppliedResponse(56L, 101L, "winner answer", 8, 12);

        assertThat(existing.getAppliedActualCostEur()).isNotNull();
        assertThat(existing.getProposedActualCostEur()).isEqualTo(existing.getAppliedActualCostEur());
    }

    @Test
    void shadowStats_marksDivergenceAsExpectedNotFailure() {
        RoutingDecisionLog log = new RoutingDecisionLog();
        log.setDiverged(true);
        log.setSelectedModelName("gpt-4o-mini");
        log.setAppliedModelName("llama3.2:1b");
        log.setSelectedEstimatedCostEur(0.02);
        log.setAppliedEstimatedCostEur(0.0);
        log.setPromptSnapshot("q");
        log.setAppliedResponseSnapshot("a");
        when(decisionLogRepository.findByCreatedAtBetweenAndMode(any(), any(), eq("SHADOW")))
                .thenReturn(List.of(log));
        when(decisionLogRepository.findByCreatedAtBetweenAndMode(any(), any(), eq("CANARY")))
                .thenReturn(List.of());

        Map<String, Object> stats = routingService.shadowStats(
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-08T00:00:00Z"));

        assertThat(stats.get("divergenceInterpretation")).isEqualTo("EXPECTED_IN_SHADOW");
        assertThat(stats.get("divergenceNote").toString()).contains("NOT a quality failure");
        assertThat(stats.get("judgeReadyDisagreements")).isEqualTo(1L);
        assertThat(stats.get("potentialSavingNote").toString()).contains("Estimate-only");
        assertThat(stats.get("qualityFeedbackLoop")).isEqualTo("HUMAN_IN_THE_LOOP");
        assertThat(stats.get("judgeSignalNote").toString()).contains("aggregates");
    }

    @Test
    void persistAndMeter_storesPromptOnlyWhenDiverged() {
        RoutingDecision decision = new RoutingDecision(
                RoutingProperties.Mode.SHADOW,
                RoutingProperties.Profile.BALANCED,
                "gpt-4o-mini",
                2L,
                "llama3.2:1b",
                0.01,
                0,
                12,
                List.of(),
                List.of(),
                Map.of()
        );
        when(decisionLogRepository.save(any())).thenAnswer(inv -> {
            RoutingDecisionLog log = inv.getArgument(0);
            log.setId(77L);
            return log;
        });

        routingService.persistAndMeter(decision, 1L, "gpt-4o-mini", false, "secret prompt", 9L, 88L);

        verify(decisionLogRepository).save(org.mockito.ArgumentMatchers.argThat(log ->
                log.getPromptSnapshot() == null && !log.isDiverged()
        ));
    }
}
