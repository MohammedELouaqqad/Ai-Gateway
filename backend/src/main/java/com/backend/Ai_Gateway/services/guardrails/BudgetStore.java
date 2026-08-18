package com.backend.Ai_Gateway.services.guardrails;

public interface BudgetStore {

    /**
     * Réserve estimatedCents sur user+global (atomique).
     * Bootstrap depuis SQL si miss (implémentation redis).
     */
    BudgetResult tryReserve(
            long userId,
            String utcDate,
            long estimatedCents,
            long userCapCents,
            long globalCapCents,
            int ttlSeconds,
            long sqlUserSpentCents,
            long sqlGlobalSpentCents
    );

    /** Libère la réservation (LLM error ou rollback). */
    void release(BudgetReservation reservation);

    /**
     * Ajuste après succès : delta = realCents - estimatedCents.
     * Si realTokens absents → delta 0 (garde l'estimé).
     */
    void confirm(BudgetReservation reservation, long realCents);

    /** Réconciliation : aligne Redis sur SUM SQL (journée UTC courante). */
    void reconcileFromSql(long userId, String utcDate, long sqlUserCents, long sqlGlobalCents, int ttlSeconds);

    long peekUserCents(long userId, String utcDate);

    long peekGlobalCents(String utcDate);
}
