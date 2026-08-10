package com.backend.Ai_Gateway.services.guardrails;

import com.backend.Ai_Gateway.config.GuardrailsProperties;
import com.backend.Ai_Gateway.config.RedisGuardProperties;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Read-only guardrails status for the current user (speed + amount usage vs. limits) —
 * consumed by the "my usage" UI. Never blocks or mutates anything, unlike
 * {@link GuardAdmissionService}. Not a Spring bean on its own — composed by
 * {@code UsageGuardService}.
 */
public final class GuardStatusReportingService {

    private final UsageLogRepository usageLogRepository;
    private final RateLimitStore rateLimitStore;
    private final RedisGuardProperties redisProps;
    private final GuardrailsRuntimeConfig config;

    public GuardStatusReportingService(
            UsageLogRepository usageLogRepository,
            RateLimitStore rateLimitStore,
            RedisGuardProperties redisProps,
            GuardrailsRuntimeConfig config
    ) {
        this.usageLogRepository = usageLogRepository;
        this.rateLimitStore = rateLimitStore;
        this.redisProps = redisProps;
        this.config = config;
    }

    public Map<String, Object> statusForUser(Long userId) {
        GuardrailsProperties cfg = config.effectiveConfig();
        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);

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
}
