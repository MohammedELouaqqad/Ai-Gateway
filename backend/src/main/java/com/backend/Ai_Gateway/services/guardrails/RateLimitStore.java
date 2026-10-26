package com.backend.Ai_Gateway.services.guardrails;

public interface RateLimitStore {

    /** Compte actuel fenêtre RPM sans réserver. */
    RateLimitResult peekRpm(long userId, int limitPerMinute);

    /** Check + ZADD atomique. */
    RateLimitResult tryAcquireRpm(long userId, int limitPerMinute);

    /** Annule un membre RPM (rollback D1). */
    void releaseRpm(long userId, String memberId);

    RateLimitResult peekRpd(long userId, int limitPerDay, String utcDate);

    RateLimitResult tryAcquireRpd(long userId, int limitPerDay, String utcDate, int ttlSeconds);

    /** Bootstrap RPD depuis COUNT SQL si miss (no-op memory si déjà suivi). */
    void bootstrapRpdIfAbsent(long userId, String utcDate, long countFromSql, int ttlSeconds);
}
