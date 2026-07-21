package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.GuardrailsProperties;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException;
import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException.Reason;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Garde-fous au point de passage LLM :
 * 1) rate limit / minute
 * 2) rate limit / jour
 * 3) budget € (user + global) — modèles payants uniquement
 */
@Service
public class UsageGuardService {

    private final GuardrailsProperties defaults;
    private final AtomicReference<GuardrailsProperties> runtime;
    private final UsageLogRepository usageLogRepository;
    private final ModelPricingService pricingService;
    private final MeterRegistry meterRegistry;

    /** timestamps ms des tentatives (fenêtre glissante 1 min) */
    private final ConcurrentHashMap<Long, Deque<Long>> minuteWindows = new ConcurrentHashMap<>();

    public UsageGuardService(
            GuardrailsProperties defaults,
            UsageLogRepository usageLogRepository,
            ModelPricingService pricingService,
            MeterRegistry meterRegistry
    ) {
        this.defaults = defaults;
        this.runtime = new AtomicReference<>(copyProps(defaults));
        this.usageLogRepository = usageLogRepository;
        this.pricingService = pricingService;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerBudgetGauges() {
        Gauge.builder("ai_gateway_budget_global_ratio", this, UsageGuardService::globalBudgetRatio)
                .description("Ratio budget global du jour (0..1+). Alerte Grafana suggérée à 0.8")
                .register(meterRegistry);
    }

    private double globalBudgetRatio() {
        GuardrailsProperties cfg = effectiveConfig();
        double cap = cfg.getBudget().getGlobalPerDayEur();
        if (cap <= 0) {
            return 0;
        }
        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);
        double spent = usageLogRepository.sumEstimatedCostBetween(dayStart, dayEnd);
        return spent / cap;
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

    public void resetRuntimeToDefaults() {
        runtime.set(copyProps(defaults));
    }

    /**
     * À appeler APRÈS prepareChat, AVANT llmProviderRouter.
     */
    public void assertAllowed(Long userId, Model model, List<ChatMessage> messages) {
        GuardrailsProperties cfg = effectiveConfig();
        if (!cfg.isEnabled()) {
            return;
        }

        Instant now = Instant.now();
        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);

        // 1) Rate limit / minute (check only — reserve after all checks pass)
        int rpm = cfg.getRateLimit().getRequestsPerMinute();
        Deque<Long> window = minuteWindows.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (window) {
            long cutoff = now.toEpochMilli() - 60_000L;
            while (!window.isEmpty() && window.peekFirst() < cutoff) {
                window.pollFirst();
            }
            if (window.size() >= rpm) {
                Instant resetsAt = Instant.ofEpochMilli(window.peekFirst() + 60_000L);
                block(Reason.RATE_LIMIT_MINUTE,
                        "Limite vitesse : max " + rpm + " requêtes / minute. Réessai après "
                                + resetsAt + ".",
                        resetsAt);
            }
        }

        // 2) Rate limit / jour
        int rpd = cfg.getRateLimit().getRequestsPerDay();
        long dayCount = usageLogRepository.countByUserIdAndCreatedAtBetween(userId, dayStart, dayEnd);
        if (dayCount >= rpd) {
            block(Reason.RATE_LIMIT_DAY,
                    "Limite équité : max " + rpd + " requêtes / jour / utilisateur. Reset à "
                            + dayEnd + " (UTC).",
                    dayEnd);
        }

        // 3) Budget (modèles payants uniquement)
        String modelName = model.getName();
        if (pricingService.isBillable(modelName)) {
            int promptEst = estimatePromptTokens(messages, cfg.getEstimate().getCharsPerToken());
            int completionEst = cfg.getEstimate().getAssumedCompletionTokens();
            double estimatedCost = pricingService.calculateCostEur(promptEst, completionEst, modelName);

            double userSpent = usageLogRepository.sumEstimatedCostByUserIdAndCreatedAtBetween(userId, dayStart, dayEnd);
            double userCap = cfg.getBudget().getUserPerDayEur();
            if (userSpent + estimatedCost > userCap) {
                block(Reason.BUDGET_USER,
                        String.format(
                                "Budget utilisateur : %.4f € déjà dépensés + ~%.4f € estimés > plafond %.2f € / jour. Reset à %s (UTC).",
                                userSpent, estimatedCost, userCap, dayEnd
                        ),
                        dayEnd);
            }

            double globalSpent = usageLogRepository.sumEstimatedCostBetween(dayStart, dayEnd);
            double globalCap = cfg.getBudget().getGlobalPerDayEur();
            if (globalSpent + estimatedCost > globalCap) {
                block(Reason.BUDGET_GLOBAL,
                        String.format(
                                "Budget global : %.4f € déjà dépensés + ~%.4f € estimés > plafond %.2f € / jour. Reset à %s (UTC).",
                                globalSpent, estimatedCost, globalCap, dayEnd
                        ),
                        dayEnd);
            }
        }

        // Reserve minute slot only when all checks passed
        synchronized (window) {
            window.addLast(now.toEpochMilli());
        }
    }

    public void recordSuccess(
            Long userId,
            Model model,
            int promptTokens,
            int completionTokens,
            long latencyMs
    ) {
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

    public Map<String, Object> statusForUser(Long userId) {
        GuardrailsProperties cfg = effectiveConfig();
        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);
        Instant now = Instant.now();

        int rpmUsed;
        Deque<Long> window = minuteWindows.get(userId);
        if (window == null) {
            rpmUsed = 0;
        } else {
            synchronized (window) {
                long cutoff = now.toEpochMilli() - 60_000L;
                while (!window.isEmpty() && window.peekFirst() < cutoff) {
                    window.pollFirst();
                }
                rpmUsed = window.size();
            }
        }

        long dayCount = usageLogRepository.countByUserIdAndCreatedAtBetween(userId, dayStart, dayEnd);
        double userSpent = usageLogRepository.sumEstimatedCostByUserIdAndCreatedAtBetween(userId, dayStart, dayEnd);
        double globalSpent = usageLogRepository.sumEstimatedCostBetween(dayStart, dayEnd);

        return Map.of(
                "enabled", cfg.isEnabled(),
                "speed", Map.of(
                        "requestsPerMinute", Map.of(
                                "limit", cfg.getRateLimit().getRequestsPerMinute(),
                                "used", rpmUsed,
                                "resetsAtHint", "sliding 60s window"
                        ),
                        "requestsPerDay", Map.of(
                                "limit", cfg.getRateLimit().getRequestsPerDay(),
                                "used", dayCount,
                                "resetsAt", dayEnd.toString()
                        )
                ),
                "amount", Map.of(
                        "userBudgetEur", Map.of(
                                "limit", cfg.getBudget().getUserPerDayEur(),
                                "used", userSpent,
                                "ratio", cfg.getBudget().getUserPerDayEur() <= 0
                                        ? 0
                                        : userSpent / cfg.getBudget().getUserPerDayEur(),
                                "resetsAt", dayEnd.toString()
                        ),
                        "globalBudgetEur", Map.of(
                                "limit", cfg.getBudget().getGlobalPerDayEur(),
                                "used", globalSpent,
                                "ratio", cfg.getBudget().getGlobalPerDayEur() <= 0
                                        ? 0
                                        : globalSpent / cfg.getBudget().getGlobalPerDayEur(),
                                "resetsAt", dayEnd.toString()
                        )
                ),
                "note", "Budget € s'applique uniquement aux modèles payants (pricing > 0). Ollama = rate limit seulement."
        );
    }

    private void block(Reason reason, String message, Instant resetsAt) {
        meterRegistry.counter("ai_gateway_requests_blocked_total", "reason", reason.metricLabel()).increment();
        throw new GuardrailsExceededException(reason, message, resetsAt);
    }

    private static int estimatePromptTokens(List<ChatMessage> messages, int charsPerToken) {
        int cpt = Math.max(charsPerToken, 1);
        if (messages == null || messages.isEmpty()) {
            return 50;
        }
        int chars = 0;
        for (ChatMessage m : messages) {
            if (m != null && m.content() != null) {
                chars += m.content().length();
            }
        }
        return Math.max(1, chars / cpt);
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
