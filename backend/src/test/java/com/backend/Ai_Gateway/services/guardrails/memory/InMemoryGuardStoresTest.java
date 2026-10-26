package com.backend.Ai_Gateway.services.guardrails.memory;

import com.backend.Ai_Gateway.services.guardrails.BudgetReservation;
import com.backend.Ai_Gateway.services.guardrails.BudgetResult;
import com.backend.Ai_Gateway.services.guardrails.BudgetStore;
import com.backend.Ai_Gateway.services.guardrails.RateLimitResult;
import com.backend.Ai_Gateway.services.guardrails.RateLimitStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryGuardStoresTest {

    @Test
    void rpmSlidingWindowBlocksAtLimit() {
        RateLimitStore store = new InMemoryRateLimitStore();
        assertThat(store.tryAcquireRpm(1, 2).outcome().name()).isEqualTo("ALLOWED");
        assertThat(store.tryAcquireRpm(1, 2).outcome().name()).isEqualTo("ALLOWED");
        assertThat(store.tryAcquireRpm(1, 2).outcome().name()).isEqualTo("BLOCKED");
    }

    @Test
    void budgetReserveAndRelease() {
        BudgetStore store = new InMemoryBudgetStore();
        BudgetResult ok = store.tryReserve(1L, "2026-07-27", 50, 100, 1000, 3600, 0, 0);
        assertThat(ok.outcome().name()).isEqualTo("ALLOWED");
        store.release(ok.reservation());
        assertThat(store.peekUserCents(1L, "2026-07-27")).isEqualTo(0);

        BudgetResult blocked = store.tryReserve(1L, "2026-07-27", 150, 100, 1000, 3600, 0, 0);
        assertThat(blocked.outcome().name()).isEqualTo("BLOCKED");
        assertThat(blocked.blockCode()).isEqualTo(1);
    }

    @Test
    void budgetConfirmAdjustsDelta() {
        BudgetStore store = new InMemoryBudgetStore();
        BudgetReservation r = store.tryReserve(2L, "2026-07-27", 100, 500, 5000, 3600, 0, 0).reservation();
        store.confirm(r, 80);
        assertThat(store.peekUserCents(2L, "2026-07-27")).isEqualTo(80);
    }
}
