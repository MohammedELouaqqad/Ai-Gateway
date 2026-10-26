package com.backend.Ai_Gateway.services.guardrails.memory;

import com.backend.Ai_Gateway.services.guardrails.RateLimitResult;
import com.backend.Ai_Gateway.services.guardrails.RateLimitStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRateLimitStore implements RateLimitStore {

    private final ConcurrentHashMap<Long, Deque<Stamp>> rpmWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> rpdCounters = new ConcurrentHashMap<>();

    private record Stamp(long epochMs, String memberId) {}

    @Override
    public RateLimitResult peekRpm(long userId, int limitPerMinute) {
        long now = System.currentTimeMillis();
        Deque<Stamp> window = rpmWindows.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (window) {
            purge(window, now);
            long used = window.size();
            if (used >= limitPerMinute) {
                return RateLimitResult.blocked(used);
            }
            return RateLimitResult.allowed(used, null);
        }
    }

    @Override
    public RateLimitResult tryAcquireRpm(long userId, int limitPerMinute) {
        long now = System.currentTimeMillis();
        String memberId = now + ":" + UUID.randomUUID();
        Deque<Stamp> window = rpmWindows.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (window) {
            purge(window, now);
            if (window.size() >= limitPerMinute) {
                return RateLimitResult.blocked(window.size());
            }
            window.addLast(new Stamp(now, memberId));
            return RateLimitResult.allowed(window.size(), memberId);
        }
    }

    @Override
    public void releaseRpm(long userId, String memberId) {
        if (memberId == null) {
            return;
        }
        Deque<Stamp> window = rpmWindows.get(userId);
        if (window == null) {
            return;
        }
        synchronized (window) {
            window.removeIf(s -> memberId.equals(s.memberId()));
        }
    }

    @Override
    public RateLimitResult peekRpd(long userId, int limitPerDay, String utcDate) {
        long used = rpdCounters.getOrDefault(rpdKey(userId, utcDate), new AtomicLong(0)).get();
        if (used >= limitPerDay) {
            return RateLimitResult.blocked(used);
        }
        return RateLimitResult.allowed(used, null);
    }

    @Override
    public RateLimitResult tryAcquireRpd(long userId, int limitPerDay, String utcDate, int ttlSeconds) {
        AtomicLong counter = rpdCounters.computeIfAbsent(rpdKey(userId, utcDate), k -> new AtomicLong(0));
        long n = counter.incrementAndGet();
        if (n > limitPerDay) {
            counter.decrementAndGet();
            return RateLimitResult.blocked(n - 1);
        }
        return RateLimitResult.allowed(n, null);
    }

    @Override
    public void bootstrapRpdIfAbsent(long userId, String utcDate, long countFromSql, int ttlSeconds) {
        rpdCounters.computeIfAbsent(rpdKey(userId, utcDate), k -> new AtomicLong(Math.max(0, countFromSql)));
    }

    private static void purge(Deque<Stamp> window, long now) {
        long cutoff = now - 60_000L;
        while (!window.isEmpty() && window.peekFirst().epochMs() < cutoff) {
            window.pollFirst();
        }
    }

    private static String rpdKey(long userId, String utcDate) {
        return userId + ":" + utcDate;
    }
}
