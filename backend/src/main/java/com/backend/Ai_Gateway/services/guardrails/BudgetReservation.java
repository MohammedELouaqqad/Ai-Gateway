package com.backend.Ai_Gateway.services.guardrails;

/**
 * Réservation budget en centimes (user + global).
 * @param estimatedCents montant réservé avant LLM
 */
public record BudgetReservation(
        long userId,
        String utcDate,
        long estimatedCents,
        boolean active
) {
    public static BudgetReservation none() {
        return new BudgetReservation(-1, null, 0, false);
    }
}
