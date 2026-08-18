package com.backend.Ai_Gateway.services.guardrails;

public record RateLimitResult(
        StoreOutcome outcome,
        long used,
        /** Identifiant membre ZSET pour rollback RPM (nullable). */
        String rpmMemberId
) {
    public static RateLimitResult allowed(long used, String memberId) {
        return new RateLimitResult(StoreOutcome.ALLOWED, used, memberId);
    }

    public static RateLimitResult blocked(long used) {
        return new RateLimitResult(StoreOutcome.BLOCKED, used, null);
    }

    public static RateLimitResult unavailable() {
        return new RateLimitResult(StoreOutcome.UNAVAILABLE, -1, null);
    }
}
