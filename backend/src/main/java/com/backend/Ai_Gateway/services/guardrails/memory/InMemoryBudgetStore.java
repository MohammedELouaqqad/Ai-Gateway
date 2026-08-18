package com.backend.Ai_Gateway.services.guardrails.memory;

import com.backend.Ai_Gateway.services.guardrails.BudgetReservation;
import com.backend.Ai_Gateway.services.guardrails.BudgetResult;
import com.backend.Ai_Gateway.services.guardrails.BudgetStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryBudgetStore implements BudgetStore {

    private final ConcurrentHashMap<String, AtomicLong> userSpent = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> globalSpent = new ConcurrentHashMap<>();

    @Override
    public BudgetResult tryReserve(
            long userId,
            String utcDate,
            long estimatedCents,
            long userCapCents,
            long globalCapCents,
            int ttlSeconds,
            long sqlUserSpentCents,
            long sqlGlobalSpentCents
    ) {
        userSpent.computeIfAbsent("u:" + userId + ":" + utcDate, k -> new AtomicLong(Math.max(0, sqlUserSpentCents)));
        globalSpent.computeIfAbsent("g:" + utcDate, k -> new AtomicLong(Math.max(0, sqlGlobalSpentCents)));

        AtomicLong user = userSpent.get("u:" + userId + ":" + utcDate);
        AtomicLong global = globalSpent.get("g:" + utcDate);

        synchronized (this) {
            long userAfter = user.addAndGet(estimatedCents);
            long globalAfter = global.addAndGet(estimatedCents);
            if (userAfter > userCapCents) {
                user.addAndGet(-estimatedCents);
                global.addAndGet(-estimatedCents);
                return BudgetResult.blocked(1, user.get(), global.get());
            }
            if (globalAfter > globalCapCents) {
                user.addAndGet(-estimatedCents);
                global.addAndGet(-estimatedCents);
                return BudgetResult.blocked(2, user.get(), global.get());
            }
            BudgetReservation reservation = new BudgetReservation(userId, utcDate, estimatedCents, true);
            return BudgetResult.allowed(reservation, userAfter, globalAfter);
        }
    }

    @Override
    public void release(BudgetReservation reservation) {
        if (reservation == null || !reservation.active()) {
            return;
        }
        adjust(reservation, -reservation.estimatedCents());
    }

    @Override
    public void confirm(BudgetReservation reservation, long realCents) {
        if (reservation == null || !reservation.active()) {
            return;
        }
        long delta = realCents - reservation.estimatedCents();
        if (delta != 0) {
            adjust(reservation, delta);
        }
    }

    @Override
    public void reconcileFromSql(long userId, String utcDate, long sqlUserCents, long sqlGlobalCents, int ttlSeconds) {
        if (userId >= 0) {
            userSpent.put("u:" + userId + ":" + utcDate, new AtomicLong(Math.max(0, sqlUserCents)));
        }
        globalSpent.put("g:" + utcDate, new AtomicLong(Math.max(0, sqlGlobalCents)));
    }

    @Override
    public long peekUserCents(long userId, String utcDate) {
        AtomicLong v = userSpent.get("u:" + userId + ":" + utcDate);
        return v == null ? 0 : v.get();
    }

    @Override
    public long peekGlobalCents(String utcDate) {
        AtomicLong v = globalSpent.get("g:" + utcDate);
        return v == null ? 0 : v.get();
    }

    private void adjust(BudgetReservation reservation, long deltaCents) {
        AtomicLong user = userSpent.get("u:" + reservation.userId() + ":" + reservation.utcDate());
        AtomicLong global = globalSpent.get("g:" + reservation.utcDate());
        if (user != null) {
            user.addAndGet(deltaCents);
        }
        if (global != null) {
            global.addAndGet(deltaCents);
        }
    }
}
