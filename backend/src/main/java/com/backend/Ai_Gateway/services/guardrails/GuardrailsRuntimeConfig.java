package com.backend.Ai_Gateway.services.guardrails;

import com.backend.Ai_Gateway.config.GuardrailsProperties;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the guardrails configuration that admins can tweak at runtime (via
 * {@code GuardrailsController}) without a redeploy, starting from the application's
 * configured defaults. Not a Spring bean on its own — composed by {@code UsageGuardService},
 * which stays the single injectable facade.
 */
public final class GuardrailsRuntimeConfig {

    private final GuardrailsProperties defaults;
    private final AtomicReference<GuardrailsProperties> runtime;

    public GuardrailsRuntimeConfig(GuardrailsProperties defaults) {
        this.defaults = defaults;
        this.runtime = new AtomicReference<>(copyProps(defaults));
    }

    public GuardrailsProperties effectiveConfig() {
        return runtime.get();
    }

    public void updateRuntimeConfig(
            Boolean enabled,
            Integer requestsPerMinute,
            Integer requestsPerDay,
            Double globalBudgetPerDayEur,
            Double userBudgetPerDayEur
    ) {
        GuardrailsProperties current = copyProps(runtime.get());
        if (enabled != null) {
            current.setEnabled(enabled);
        }
        if (requestsPerMinute != null && requestsPerMinute > 0) {
            current.getRateLimit().setRequestsPerMinute(requestsPerMinute);
        }
        if (requestsPerDay != null && requestsPerDay > 0) {
            current.getRateLimit().setRequestsPerDay(requestsPerDay);
        }
        if (globalBudgetPerDayEur != null && globalBudgetPerDayEur >= 0) {
            current.getBudget().setGlobalPerDayEur(globalBudgetPerDayEur);
        }
        if (userBudgetPerDayEur != null && userBudgetPerDayEur >= 0) {
            current.getBudget().setUserPerDayEur(userBudgetPerDayEur);
        }
        runtime.set(current);
    }

    public void resetToDefaults() {
        runtime.set(copyProps(defaults));
    }

    private static GuardrailsProperties copyProps(GuardrailsProperties src) {
        GuardrailsProperties copy = new GuardrailsProperties();
        copy.setEnabled(src.isEnabled());
        GuardrailsProperties.RateLimit rl = new GuardrailsProperties.RateLimit();
        rl.setRequestsPerMinute(src.getRateLimit().getRequestsPerMinute());
        rl.setRequestsPerDay(src.getRateLimit().getRequestsPerDay());
        copy.setRateLimit(rl);
        GuardrailsProperties.Budget b = new GuardrailsProperties.Budget();
        b.setGlobalPerDayEur(src.getBudget().getGlobalPerDayEur());
        b.setUserPerDayEur(src.getBudget().getUserPerDayEur());
        copy.setBudget(b);
        GuardrailsProperties.Estimate e = new GuardrailsProperties.Estimate();
        e.setAssumedCompletionTokens(src.getEstimate().getAssumedCompletionTokens());
        e.setCharsPerToken(src.getEstimate().getCharsPerToken());
        copy.setEstimate(e);
        copy.setPricing(src.getPricing());
        return copy;
    }
}
