package com.backend.Ai_Gateway.services.guardrails;

import com.backend.Ai_Gateway.config.GuardrailsProperties;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException.Reason;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import com.backend.Ai_Gateway.services.ModelPricingService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * All Micrometer/Prometheus interaction for the guardrails pipeline lives here — every
 * {@code ai_gateway_*} counter/timer/gauge the guardrails emit is registered or incremented
 * through this one class, so cardinality/tagging choices stay in one place. Not a Spring
 * bean on its own — composed by {@code UsageGuardService}.
 */
public final class GuardMetricsRecorder {

    private final MeterRegistry meterRegistry;
    private final ModelPricingService pricingService;

    public GuardMetricsRecorder(
            MeterRegistry meterRegistry,
            ModelPricingService pricingService,
            UsageLogRepository usageLogRepository,
            GuardrailsRuntimeConfig config
    ) {
        this.meterRegistry = meterRegistry;
        this.pricingService = pricingService;
        Gauge.builder("ai_gateway_budget_global_ratio",
                        () -> globalBudgetRatio(usageLogRepository, config))
                .description("Ratio budget global du jour (0..1+)")
                .register(meterRegistry);
    }

    private static double globalBudgetRatio(UsageLogRepository usageLogRepository, GuardrailsRuntimeConfig config) {
        GuardrailsProperties cfg = config.effectiveConfig();
        double cap = cfg.getBudget().getGlobalPerDayEur();
        if (cap <= 0) {
            return 0;
        }
        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);
        double spent = usageLogRepository.sumEstimatedCostBetween(dayStart, dayEnd);
        return spent / cap;
    }

    public void recordSuccess(Long userId, Model model, int promptTokens, int completionTokens, long latencyMs) {
        String provider = model.getProvider() != null ? model.getProvider().getName() : "unknown";
        String location = "local";
        if (model.getProvider() != null && model.getProvider().getLocation() == ProviderLocation.CLOUD) {
            location = "cloud";
        }
        String modelName = model.getName() != null ? model.getName() : "unknown";
        String userLabel = String.valueOf(userId);

        int prompt = Math.max(promptTokens, 0);
        int completion = Math.max(completionTokens, 0);
        double cost = pricingService.calculateCostEur(prompt, completion, modelName);

        meterRegistry.counter("ai_gateway_tokens_total",
                "provider", provider, "model", modelName, "user", userLabel
        ).increment(prompt + completion);

        meterRegistry.counter("ai_gateway_cost_eur_total",
                "provider", provider, "model", modelName, "user", userLabel
        ).increment(cost);

        Timer.builder("ai_gateway_llm_latency")
                .description("Latence appels LLM sortants")
                .tag("provider", provider)
                .tag("location", location)
                .publishPercentiles(0.5, 0.95)
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Provider-attributed failure (LLM call itself failed, not a guardrail block).
     * {@code reason} is a short label (e.g. the exception's simple class name, or
     * "timeout") — kept low-cardinality on purpose, unlike {@code model}/{@code provider}
     * which are already bounded by the admin-managed catalog.
     */
    public void recordFailure(Model model, String reason) {
        String provider = model.getProvider() != null ? model.getProvider().getName() : "unknown";
        String modelName = model.getName() != null ? model.getName() : "unknown";
        meterRegistry.counter("ai_gateway_provider_errors_total",
                "provider", provider, "model", modelName, "reason", reason != null ? reason : "unknown"
        ).increment();
    }

    /**
     * {@code user} tag lets an admin spot ONE user looping (repeated RPM/budget blocks)
     * instead of only seeing a system-wide block rate — same tagging pattern already used
     * by {@link #recordSuccess} for tokens/cost, so no new cardinality precedent here.
     */
    public void recordBlocked(Reason reason, Long userId) {
        meterRegistry.counter("ai_gateway_requests_blocked_total",
                "reason", reason.metricLabel(), "user", String.valueOf(userId)
        ).increment();
    }

    public void recordRedisFailOpen(Reason reason) {
        meterRegistry.counter("ai_gateway_redis_fail_open_total", "reason", reason.metricLabel()).increment();
    }
}
