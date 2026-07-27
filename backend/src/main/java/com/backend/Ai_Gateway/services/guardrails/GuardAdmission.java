package com.backend.Ai_Gateway.services.guardrails;

/** Contexte retourné par assertAllowed pour release/confirm budget. */
public record GuardAdmission(BudgetReservation budgetReservation) {
    public static GuardAdmission empty() {
        return new GuardAdmission(BudgetReservation.none());
    }
}
