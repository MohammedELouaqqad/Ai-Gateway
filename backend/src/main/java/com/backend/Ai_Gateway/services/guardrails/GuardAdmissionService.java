package com.backend.Ai_Gateway.services.guardrails;

import com.backend.Ai_Gateway.config.GuardrailsProperties;
import com.backend.Ai_Gateway.config.RedisGuardProperties;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException;
import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException.Reason;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import com.backend.Ai_Gateway.services.ModelPricingService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The actual "is this request allowed?" decision: RPM → RPD → Budget (D1: a budget block
 * never consumes RPM/RPD). D2: RPD counts accepted attempts. D3: no requestId idempotency
 * in v1. Not a Spring bean on its own — composed by {@code UsageGuardService}.
 */
public final class GuardAdmissionService {

    private final UsageLogRepository usageLogRepository;
    private final ModelPricingService pricingService;
    private final RateLimitStore rateLimitStore;
    private final BudgetStore budgetStore;
    private final RedisGuardProperties redisProps;
    private final GuardrailsRuntimeConfig config;
    private final GuardMetricsRecorder metrics;

    public GuardAdmissionService(
            UsageLogRepository usageLogRepository,
            ModelPricingService pricingService,
            RateLimitStore rateLimitStore,
            BudgetStore budgetStore,
            RedisGuardProperties redisProps,
            GuardrailsRuntimeConfig config,
            GuardMetricsRecorder metrics
    ) {
        this.usageLogRepository = usageLogRepository;
        this.pricingService = pricingService;
        this.rateLimitStore = rateLimitStore;
        this.budgetStore = budgetStore;
        this.redisProps = redisProps;
        this.config = config;
        this.metrics = metrics;
    }

    /**
     * À appeler APRÈS prepareChat, AVANT llmProviderRouter.
     * @return admission avec réservation budget (à release/confirm)
     */
    public GuardAdmission assertAllowed(Long userId, Model model, List<ChatMessage> messages) {
        GuardrailsProperties cfg = config.effectiveConfig();
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
        if (pricingService.isBillable(modelName)) {
            int promptEst = estimatePromptTokens(
                    messages, cfg.getEstimate().getCharsPerToken(), cfg.getEstimate().getImageFlatTokens());
            int completionEst = cfg.getEstimate().getAssumedCompletionTokens();
            double estimatedEur = pricingService.calculateCostEur(promptEst, completionEst, modelName);
            long estimatedCents = Math.max(1, Math.round(estimatedEur * 100.0));

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
            metrics.recordRedisFailOpen(reason);
            return;
        }
        block(userId, reason,
                "Rate limit indisponible (Redis). Réessayez plus tard. Limite=" + limit,
                reason == Reason.RATE_LIMIT_DAY ? dayEnd : Instant.now().plusSeconds(30));
    }

    private void block(Long userId, Reason reason, String message, Instant resetsAt) {
        metrics.recordBlocked(reason, userId);
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

    private static int estimatePromptTokens(List<ChatMessage> messages, int charsPerToken, int imageFlatTokens) {
        int cpt = Math.max(charsPerToken, 1);
        if (messages == null || messages.isEmpty()) {
            return 50;
        }
        int chars = 0;
        int imageCount = 0;
        for (ChatMessage m : messages) {
            if (m == null) {
                continue;
            }
            if (m.content() != null) {
                chars += m.content().length();
            }
            if (m.attachments() != null) {
                imageCount += m.attachments().size();
            }
        }
        return Math.max(1, chars / cpt) + imageCount * Math.max(imageFlatTokens, 0);
    }
}
