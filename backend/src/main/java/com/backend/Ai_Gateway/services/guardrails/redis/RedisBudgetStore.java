package com.backend.Ai_Gateway.services.guardrails.redis;

import com.backend.Ai_Gateway.services.guardrails.BudgetReservation;
import com.backend.Ai_Gateway.services.guardrails.BudgetResult;
import com.backend.Ai_Gateway.services.guardrails.BudgetStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public class RedisBudgetStore implements BudgetStore {

    private static final Logger log = LoggerFactory.getLogger(RedisBudgetStore.class);

    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;
    private final DefaultRedisScript<List> reserveScript;
    private final DefaultRedisScript<Long> adjustScript;

    public RedisBudgetStore(
            StringRedisTemplate redis,
            MeterRegistry meterRegistry,
            DefaultRedisScript<List> reserveScript,
            DefaultRedisScript<Long> adjustScript
    ) {
        this.redis = redis;
        this.meterRegistry = meterRegistry;
        this.reserveScript = reserveScript;
        this.adjustScript = adjustScript;
    }

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
        try {
            bootstrapIfAbsent(userKey(userId, utcDate), sqlUserSpentCents, ttlSeconds, "budget_user");
            bootstrapIfAbsent(globalKey(utcDate), sqlGlobalSpentCents, ttlSeconds, "budget_global");

            List<?> result = redis.execute(
                    reserveScript,
                    Arrays.asList(userKey(userId, utcDate), globalKey(utcDate)),
                    String.valueOf(estimatedCents),
                    String.valueOf(userCapCents),
                    String.valueOf(globalCapCents),
                    String.valueOf(ttlSeconds)
            );
            if (result == null || result.isEmpty()) {
                return BudgetResult.unavailable();
            }
            long ok = toLong(result.get(0));
            if (ok == 1) {
                long userAfter = toLong(result.get(1));
                long globalAfter = toLong(result.get(2));
                BudgetReservation reservation = new BudgetReservation(userId, utcDate, estimatedCents, true);
                return BudgetResult.allowed(reservation, userAfter, globalAfter);
            }
            int code = (int) toLong(result.get(1));
            long userCents = toLong(result.get(2));
            long globalCents = toLong(result.get(3));
            return BudgetResult.blocked(code, userCents, globalCents);
        } catch (Exception e) {
            meterRegistry.counter("ai_gateway_redis_errors_total", "op", "budget_reserve").increment();
            log.warn("Redis budget reserve unavailable: {}", e.toString());
            return BudgetResult.unavailable();
        }
    }

    @Override
    public void release(BudgetReservation reservation) {
        if (reservation == null || !reservation.active() || reservation.estimatedCents() == 0) {
            return;
        }
        try {
            redis.execute(
                    adjustScript,
                    Arrays.asList(userKey(reservation.userId(), reservation.utcDate()), globalKey(reservation.utcDate())),
                    String.valueOf(-reservation.estimatedCents())
            );
            meterRegistry.counter("ai_gateway_reservation_release_total").increment();
        } catch (Exception e) {
            meterRegistry.counter("ai_gateway_redis_errors_total", "op", "budget_release").increment();
            log.warn("Redis budget release failed: {}", e.toString());
        }
    }

    @Override
    public void confirm(BudgetReservation reservation, long realCents) {
        if (reservation == null || !reservation.active()) {
            return;
        }
        long delta = realCents - reservation.estimatedCents();
        if (delta == 0) {
            return;
        }
        try {
            redis.execute(
                    adjustScript,
                    Arrays.asList(userKey(reservation.userId(), reservation.utcDate()), globalKey(reservation.utcDate())),
                    String.valueOf(delta)
            );
        } catch (Exception e) {
            meterRegistry.counter("ai_gateway_redis_errors_total", "op", "budget_confirm").increment();
            log.warn("Redis budget confirm failed: {}", e.toString());
        }
    }

    @Override
    public void reconcileFromSql(long userId, String utcDate, long sqlUserCents, long sqlGlobalCents, int ttlSeconds) {
        try {
            if (userId >= 0) {
                redis.opsForValue().set(userKey(userId, utcDate), String.valueOf(Math.max(0, sqlUserCents)),
                        Duration.ofSeconds(ttlSeconds));
            }
            redis.opsForValue().set(globalKey(utcDate), String.valueOf(Math.max(0, sqlGlobalCents)),
                    Duration.ofSeconds(ttlSeconds));
            meterRegistry.counter("ai_gateway_reconciliation_runs_total").increment();
        } catch (Exception e) {
            meterRegistry.counter("ai_gateway_redis_errors_total", "op", "budget_reconcile").increment();
            log.warn("Redis budget reconcile failed: {}", e.toString());
        }
    }

    @Override
    public long peekUserCents(long userId, String utcDate) {
        try {
            String raw = redis.opsForValue().get(userKey(userId, utcDate));
            return raw == null ? 0 : Long.parseLong(raw);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public long peekGlobalCents(String utcDate) {
        try {
            String raw = redis.opsForValue().get(globalKey(utcDate));
            return raw == null ? 0 : Long.parseLong(raw);
        } catch (Exception e) {
            return -1;
        }
    }

    private void bootstrapIfAbsent(String key, long sqlCents, int ttlSeconds, String kind) {
        Boolean set = redis.opsForValue().setIfAbsent(key, String.valueOf(Math.max(0, sqlCents)),
                Duration.ofSeconds(ttlSeconds));
        if (Boolean.TRUE.equals(set)) {
            meterRegistry.counter("ai_gateway_bootstrap_sql_total", "kind", kind).increment();
        }
    }

    private static String userKey(long userId, String utcDate) {
        return "budget:user:" + userId + ":" + utcDate;
    }

    private static String globalKey(String utcDate) {
        return "budget:global:" + utcDate;
    }

    private static long toLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(o));
    }
}
