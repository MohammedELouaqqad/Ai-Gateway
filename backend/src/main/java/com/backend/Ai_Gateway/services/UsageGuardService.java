package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.GuardrailsProperties;
import com.backend.Ai_Gateway.config.RedisGuardProperties;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException;
import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException.Reason;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import com.backend.Ai_Gateway.services.guardrails.BudgetReservation;
import com.backend.Ai_Gateway.services.guardrails.BudgetResult;
import com.backend.Ai_Gateway.services.guardrails.BudgetStore;
import com.backend.Ai_Gateway.services.guardrails.GuardAdmission;
import com.backend.Ai_Gateway.services.guardrails.RateLimitResult;
import com.backend.Ai_Gateway.services.guardrails.RateLimitStore;
import com.backend.Ai_Gateway.services.guardrails.StoreOutcome;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Garde-fous LLM : RPM → RPD → Budget (D1: budget block ne consomme pas RPM/RPD).
 * D2: RPD = tentative acceptée. D3: pas d'idempotence requestId en v1.
 */
@Service
public class UsageGuardService {

    private final GuardrailsProperties defaults;
    private final AtomicReference<GuardrailsProperties> runtime;
    private final UsageLogRepository usageLogRepository;
    private final ModelPricingService pricingService;
    private final MeterRegistry meterRegistry;
    private final RateLimitStore rateLimitStore;
    private final BudgetStore budgetStore;
    private final RedisGuardProperties redisProps;

    public UsageGuardService(
            GuardrailsProperties defaults,
            UsageLogRepository usageLogRepository,
            ModelPricingService pricingService,
            MeterRegistry meterRegistry,
            RateLimitStore rateLimitStore,
            BudgetStore budgetStore,
            RedisGuardProperties redisProps
    ) {
        this.defaults = defaults;
        this.runtime = new AtomicReference<>(copyProps(defaults));
        this.usageLogRepository = usageLogRepository;
        this.pricingService = pricingService;
        this.meterRegistry = meterRegistry;
        this.rateLimitStore = rateLimitStore;
        this.budgetStore = budgetStore;
        this.redisProps = redisProps;
    }

    @PostConstruct
    void registerBudgetGauges() {
        Gauge.builder("ai_gateway_budget_global_ratio", this, UsageGuardService::globalBudgetRatio)
                .description("Ratio budget global du jour (0..1+)")
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
     * @return admission avec réservation budget (à release/confirm)
     */
    public GuardAdmission assertAllowed(Long userId, Model model, List<ChatMessage> messages) {
        GuardrailsProperties cfg = effectiveConfig();
        if (!cfg.isEnabled()) {
            return GuardAdmission.empty();
        }

        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);
        String utcDate = LocalDate.now(ZoneOffset.UTC).toString();
        int rpdTtl = secondsUntilEndOfUtcDayPlusMargin();
        int budgetTtl = rpdTtl;

        int rpm = cfg.getRateLimit().getRequestsPerMinute();
        int rpd = cfg.getRateLimit().getRequestsPerDay();

        // --- Peek RPM (no consume) ---
        RateLimitResult rpmPeek = rateLimitStore.peekRpm(userId, rpm);
        handleRateUnavailable(userId, rpmPeek, Reason.RATE_LIMIT_MINUTE, rpm, dayEnd);
        if (rpmPeek.outcome() == StoreOutcome.BLOCKED) {
            block(userId, Reason.RATE_LIMIT_MINUTE,
                    "Limite vitesse : max " + rpm + " requêtes / minute.",
                    Instant.now().plusSeconds(60));
        }

        // --- Peek RPD + bootstrap ---
        long sqlDayCount = usageLogRepository.countByUserIdAndCreatedAtBetween(userId, dayStart, dayEnd);
        rateLimitStore.bootstrapRpdIfAbsent(userId, utcDate, sqlDayCount, rpdTtl);
        RateLimitResult rpdPeek = rateLimitStore.peekRpd(userId, rpd, utcDate);
        handleRateUnavailable(userId, rpdPeek, Reason.RATE_LIMIT_DAY, rpd, dayEnd);
        if (rpdPeek.outcome() == StoreOutcome.BLOCKED) {
            block(userId, Reason.RATE_LIMIT_DAY,
                    "Limite équité : max " + rpd + " requêtes / jour / utilisateur. Reset à "
                            + dayEnd + " (UTC).",
                    dayEnd);
        }

        // --- Budget reserve (billable only) BEFORE committing RPM/RPD (D1) ---
        BudgetReservation reservation = BudgetReservation.none();
        String modelName = model.getName();
        long estimatedCents = 0;
        if (pricingService.isBillable(modelName)) {
            int promptEst = estimatePromptTokens(messages, cfg.getEstimate().getCharsPerToken());
            int completionEst = cfg.getEstimate().getAssumedCompletionTokens();
            double estimatedEur = pricingService.calculateCostEur(promptEst, completionEst, modelName);
            estimatedCents = Math.max(1, Math.round(estimatedEur * 100.0));

            long sqlUserCents = Math.round(
                    usageLogRepository.sumEstimatedCostByUserIdAndCreatedAtBetween(userId, dayStart, dayEnd) * 100.0);
            long sqlGlobalCents = Math.round(
                    usageLogRepository.sumEstimatedCostBetween(dayStart, dayEnd) * 100.0);
            long userCapCents = Math.round(cfg.getBudget().getUserPerDayEur() * 100.0);
            long globalCapCents = Math.round(cfg.getBudget().getGlobalPerDayEur() * 100.0);

            BudgetResult budgetResult = budgetStore.tryReserve(
                    userId, utcDate, estimatedCents, userCapCents, globalCapCents, budgetTtl,
                    sqlUserCents, sqlGlobalCents
            );

            if (budgetResult.outcome() == StoreOutcome.UNAVAILABLE) {
                // Fallback SUM Postgres (never fail-open for paid)
                if (sqlUserCents + estimatedCents > userCapCents) {
                    block(userId, Reason.BUDGET_USER, budgetUserMsg(sqlUserCents / 100.0, estimatedEur,
                            cfg.getBudget().getUserPerDayEur(), dayEnd), dayEnd);
                }
                if (sqlGlobalCents + estimatedCents > globalCapCents) {
                    block(userId, Reason.BUDGET_GLOBAL, budgetGlobalMsg(sqlGlobalCents / 100.0, estimatedEur,
                            cfg.getBudget().getGlobalPerDayEur(), dayEnd), dayEnd);
                }
                reservation = BudgetReservation.none();
            } else if (budgetResult.outcome() == StoreOutcome.BLOCKED) {
                if (budgetResult.blockCode() == 1) {
                    block(userId, Reason.BUDGET_USER, budgetUserMsg(budgetResult.userSpentCents() / 100.0,
                            estimatedEur, cfg.getBudget().getUserPerDayEur(), dayEnd), dayEnd);
                }
                block(userId, Reason.BUDGET_GLOBAL, budgetGlobalMsg(budgetResult.globalSpentCents() / 100.0,
                        estimatedEur, cfg.getBudget().getGlobalPerDayEur(), dayEnd), dayEnd);
            } else {
                reservation = budgetResult.reservation();
            }
        }

        // --- Commit RPM then RPD (only after budget OK) ---
        RateLimitResult rpmCommit = rateLimitStore.tryAcquireRpm(userId, rpm);
        if (rpmCommit.outcome() == StoreOutcome.UNAVAILABLE) {
            if (!redisProps.getRateLimit().isFailOpen()) {
                budgetStore.release(reservation);
            }
            handleRateUnavailable(userId, rpmCommit, Reason.RATE_LIMIT_MINUTE, rpm, dayEnd);
        } else if (rpmCommit.outcome() == StoreOutcome.BLOCKED) {
            budgetStore.release(reservation);
            block(userId, Reason.RATE_LIMIT_MINUTE,
                    "Limite vitesse : max " + rpm + " requêtes / minute.",
                    Instant.now().plusSeconds(60));
        }

        RateLimitResult rpdCommit = rateLimitStore.tryAcquireRpd(userId, rpd, utcDate, rpdTtl);
        if (rpdCommit.outcome() == StoreOutcome.UNAVAILABLE) {
            if (!redisProps.getRateLimit().isFailOpen()) {
                rateLimitStore.releaseRpm(userId, rpmCommit.rpmMemberId());
                budgetStore.release(reservation);
            }
            handleRateUnavailable(userId, rpdCommit, Reason.RATE_LIMIT_DAY, rpd, dayEnd);
        } else if (rpdCommit.outcome() == StoreOutcome.BLOCKED) {
            rateLimitStore.releaseRpm(userId, rpmCommit.rpmMemberId());
            budgetStore.release(reservation);
            block(userId, Reason.RATE_LIMIT_DAY,
                    "Limite équité : max " + rpd + " requêtes / jour / utilisateur. Reset à "
                            + dayEnd + " (UTC).",
                    dayEnd);
        }

        return new GuardAdmission(reservation);
    }

    public void releaseBudgetReservation(BudgetReservation reservation) {
        budgetStore.release(reservation);
    }

    public void confirmBudgetReservation(BudgetReservation reservation, Model model, int promptTokens, int completionTokens) {
        if (reservation == null || !reservation.active()) {
            return;
        }
        if (promptTokens <= 0 && completionTokens <= 0) {
            // Tokens absents → garder l'estimé (delta 0)
            return;
        }
        double realEur = pricingService.calculateCostEur(
                Math.max(promptTokens, 0),
                Math.max(completionTokens, 0),
                model.getName()
        );
        long realCents = Math.round(realEur * 100.0);
        budgetStore.confirm(reservation, realCents);
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

    public Map<String, Object> statusForUser(Long userId) {
        GuardrailsProperties cfg = effectiveConfig();
        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);
        String utcDate = LocalDate.now(ZoneOffset.UTC).toString();

        RateLimitResult rpm = rateLimitStore.peekRpm(userId, cfg.getRateLimit().getRequestsPerMinute());
        long rpmUsed = rpm.used() >= 0 ? rpm.used() : 0;

        long dayCount = usageLogRepository.countByUserIdAndCreatedAtBetween(userId, dayStart, dayEnd);
        double userSpent = usageLogRepository.sumEstimatedCostByUserIdAndCreatedAtBetween(userId, dayStart, dayEnd);
        double globalSpent = usageLogRepository.sumEstimatedCostBetween(dayStart, dayEnd);

        return Map.of(
                "enabled", cfg.isEnabled(),
                "redisEnabled", redisProps.isEnabled(),
                "rateLimitFailOpen", redisProps.getRateLimit().isFailOpen(),
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
                "note", "D1: budget block does not consume RPM/RPD. D2: RPD counts accepted attempts. "
                        + "D3: no requestId idempotency in v1. Budget € = paid models only."
        );
    }

    /** Réconciliation globale (job) — récupère réservations orphelines après crash. */
    public void reconcileGlobalBudgetFromSql() {
        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);
        String utcDate = LocalDate.now(ZoneOffset.UTC).toString();
        long sqlGlobalCents = Math.round(usageLogRepository.sumEstimatedCostBetween(dayStart, dayEnd) * 100.0);
        budgetStore.reconcileFromSql(-1, utcDate, 0, sqlGlobalCents, secondsUntilEndOfUtcDayPlusMargin());
    }

    private void handleRateUnavailable(Long userId, RateLimitResult result, Reason reason, int limit, Instant dayEnd) {
        if (result.outcome() != StoreOutcome.UNAVAILABLE) {
            return;
        }
        if (redisProps.getRateLimit().isFailOpen()) {
            meterRegistry.counter("ai_gateway_redis_fail_open_total", "reason", reason.metricLabel()).increment();
            return;
        }
        block(userId, reason,
                "Rate limit indisponible (Redis). Réessayez plus tard. Limite=" + limit,
                reason == Reason.RATE_LIMIT_DAY ? dayEnd : Instant.now().plusSeconds(30));
    }

    /**
     * {@code user} tag lets an admin spot ONE user looping (repeated RPM/budget blocks)
     * instead of only seeing a system-wide block rate — same tagging pattern already used
     * by {@link #recordSuccess} for tokens/cost, so no new cardinality precedent here.
     */
    private void block(Long userId, Reason reason, String message, Instant resetsAt) {
        meterRegistry.counter("ai_gateway_requests_blocked_total",
                "reason", reason.metricLabel(), "user", String.valueOf(userId)
        ).increment();
        throw new GuardrailsExceededException(reason, message, resetsAt);
    }

    private static String budgetUserMsg(double spent, double est, double cap, Instant dayEnd) {
        return String.format(
                "Budget utilisateur : %.4f € déjà dépensés + ~%.4f € estimés > plafond %.2f € / jour. Reset à %s (UTC).",
                spent, est, cap, dayEnd
        );
    }

    private static String budgetGlobalMsg(double spent, double est, double cap, Instant dayEnd) {
        return String.format(
                "Budget global : %.4f € déjà dépensés + ~%.4f € estimés > plafond %.2f € / jour. Reset à %s (UTC).",
                spent, est, cap, dayEnd
        );
    }

    private static int secondsUntilEndOfUtcDayPlusMargin() {
        Instant dayEnd = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long sec = ChronoUnit.SECONDS.between(Instant.now(), dayEnd) + 3600;
        return (int) Math.max(3600, Math.min(sec, Integer.MAX_VALUE));
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
