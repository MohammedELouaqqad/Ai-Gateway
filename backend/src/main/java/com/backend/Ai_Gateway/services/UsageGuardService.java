package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.GuardrailsProperties;
import com.backend.Ai_Gateway.config.RedisGuardProperties;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import com.backend.Ai_Gateway.services.guardrails.BudgetReservation;
import com.backend.Ai_Gateway.services.guardrails.BudgetStore;
import com.backend.Ai_Gateway.services.guardrails.GuardAdmission;
import com.backend.Ai_Gateway.services.guardrails.GuardAdmissionService;
import com.backend.Ai_Gateway.services.guardrails.GuardMetricsRecorder;
import com.backend.Ai_Gateway.services.guardrails.GuardStatusReportingService;
import com.backend.Ai_Gateway.services.guardrails.GuardrailsRuntimeConfig;
import com.backend.Ai_Gateway.services.guardrails.RateLimitStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Garde-fous LLM : RPM → RPD → Budget (D1: budget block ne consomme pas RPM/RPD).
 * D2: RPD = tentative acceptée. D3: pas d'idempotence requestId en v1.
 *
 * <p>Thin facade kept as the single injectable bean (so every existing call site —
 * {@link ChatUsageSupport}, {@code GuardrailsController},
 * {@link com.backend.Ai_Gateway.services.guardrails.BudgetReconciliationJob} — is unaffected
 * by this split). The actual logic lives in four focused, independently testable
 * collaborators: {@link GuardrailsRuntimeConfig} (runtime-tunable config),
 * {@link GuardAdmissionService} (the RPM/RPD/budget decision + reservation lifecycle),
 * {@link GuardMetricsRecorder} (all Micrometer interaction), and
 * {@link GuardStatusReportingService} (read-only usage status for the UI).
 */
@Service
public class UsageGuardService {

    private final GuardrailsRuntimeConfig config;
    private final GuardMetricsRecorder metrics;
    private final GuardAdmissionService admission;
    private final GuardStatusReportingService reporting;

    public UsageGuardService(
            GuardrailsProperties defaults,
            UsageLogRepository usageLogRepository,
            ModelPricingService pricingService,
            MeterRegistry meterRegistry,
            RateLimitStore rateLimitStore,
            BudgetStore budgetStore,
            RedisGuardProperties redisProps
    ) {
        this.config = new GuardrailsRuntimeConfig(defaults);
        this.metrics = new GuardMetricsRecorder(meterRegistry, pricingService, usageLogRepository, config);
        this.admission = new GuardAdmissionService(
                usageLogRepository, pricingService, rateLimitStore, budgetStore, redisProps, config, metrics);
        this.reporting = new GuardStatusReportingService(usageLogRepository, rateLimitStore, redisProps, config);
    }

    public GuardrailsProperties effectiveConfig() {
        return config.effectiveConfig();
    }

    public void updateRuntimeConfig(
            Boolean enabled,
            Integer requestsPerMinute,
            Integer requestsPerDay,
            Double globalBudgetPerDayEur,
            Double userBudgetPerDayEur
    ) {
        config.updateRuntimeConfig(enabled, requestsPerMinute, requestsPerDay, globalBudgetPerDayEur, userBudgetPerDayEur);
    }

    public void resetRuntimeToDefaults() {
        config.resetToDefaults();
    }

    /**
     * À appeler APRÈS prepareChat, AVANT llmProviderRouter.
     * @return admission avec réservation budget (à release/confirm)
     */
    public GuardAdmission assertAllowed(Long userId, Model model, List<ChatMessage> messages) {
        return admission.assertAllowed(userId, model, messages);
    }

    public void releaseBudgetReservation(BudgetReservation reservation) {
        admission.releaseBudgetReservation(reservation);
    }

    public void confirmBudgetReservation(BudgetReservation reservation, Model model, int promptTokens, int completionTokens) {
        admission.confirmBudgetReservation(reservation, model, promptTokens, completionTokens);
    }

    public void recordSuccess(Long userId, Model model, int promptTokens, int completionTokens, long latencyMs) {
        metrics.recordSuccess(userId, model, promptTokens, completionTokens, latencyMs);
    }

    /**
     * Provider-attributed failure (LLM call itself failed, not a guardrail block).
     * {@code reason} is a short label (e.g. the exception's simple class name, or
     * "timeout") — kept low-cardinality on purpose, unlike {@code model}/{@code provider}
     * which are already bounded by the admin-managed catalog.
     */
    public void recordFailure(Model model, String reason) {
        metrics.recordFailure(model, reason);
    }

    public Map<String, Object> statusForUser(Long userId) {
        return reporting.statusForUser(userId);
    }

    /** Réconciliation globale (job) — récupère réservations orphelines après crash. */
    public void reconcileGlobalBudgetFromSql() {
        admission.reconcileGlobalBudgetFromSql();
    }
}
