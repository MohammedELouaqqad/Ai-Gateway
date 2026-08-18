package com.backend.Ai_Gateway.services.guardrails;

public record BudgetResult(
        StoreOutcome outcome,
        /** 1 = user cap, 2 = global cap, 0 = n/a */
        int blockCode,
        BudgetReservation reservation,
        long userSpentCents,
        long globalSpentCents
) {
    public static BudgetResult allowed(BudgetReservation reservation, long userCents, long globalCents) {
        return new BudgetResult(StoreOutcome.ALLOWED, 0, reservation, userCents, globalCents);
    }

    public static BudgetResult blocked(int code, long userCents, long globalCents) {
        return new BudgetResult(StoreOutcome.BLOCKED, code, BudgetReservation.none(), userCents, globalCents);
    }

    public static BudgetResult unavailable() {
        return new BudgetResult(StoreOutcome.UNAVAILABLE, 0, BudgetReservation.none(), -1, -1);
    }

    public static BudgetResult skipped() {
        return new BudgetResult(StoreOutcome.ALLOWED, 0, BudgetReservation.none(), 0, 0);
    }
}
