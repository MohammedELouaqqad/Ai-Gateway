package com.backend.Ai_Gateway.services.guardrails.redis;

import com.backend.Ai_Gateway.services.guardrails.RateLimitResult;
import com.backend.Ai_Gateway.services.guardrails.RateLimitStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RedisRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);
    private static final long WINDOW_MS = 60_000L;
    private static final int KEY_TTL_SEC = 120;

    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;
    private final DefaultRedisScript<Long> peekRpmScript;
    private final DefaultRedisScript<List> tryRpmScript;
    private final DefaultRedisScript<Long> releaseRpmScript;
    private final DefaultRedisScript<List> tryRpdScript;

    public RedisRateLimitStore(
            StringRedisTemplate redis,
            MeterRegistry meterRegistry,
            DefaultRedisScript<Long> peekRpmScript,
            DefaultRedisScript<List> tryRpmScript,
            DefaultRedisScript<Long> releaseRpmScript,
            DefaultRedisScript<List> tryRpdScript
    ) {
        this.redis = redis;
        this.meterRegistry = meterRegistry;
        this.peekRpmScript = peekRpmScript;
        this.tryRpmScript = tryRpmScript;
        this.releaseRpmScript = releaseRpmScript;
        this.tryRpdScript = tryRpdScript;
    }

    @Override
    public RateLimitResult peekRpm(long userId, int limitPerMinute) {
        try {
            Long count = redis.execute(
                    peekRpmScript,
                    Collections.singletonList(rpmKey(userId)),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(WINDOW_MS)
            );
            long used = count != null ? count : 0;
            if (used >= limitPerMinute) {
                return RateLimitResult.blocked(used);
            }
            return RateLimitResult.allowed(used, null);
        } catch (Exception e) {
            return unavailable(e);
        }
    }

    @Override
    public RateLimitResult tryAcquireRpm(long userId, int limitPerMinute) {
        try {
            long now = System.currentTimeMillis();
            String memberId = now + ":" + UUID.randomUUID();
            List<?> result = redis.execute(
                    tryRpmScript,
                    Collections.singletonList(rpmKey(userId)),
                    String.valueOf(now),
                    String.valueOf(WINDOW_MS),
                    String.valueOf(limitPerMinute),
                    memberId,
                    String.valueOf(KEY_TTL_SEC)
            );
            if (result == null || result.size() < 2) {
                return unavailable(new IllegalStateException("empty rpm lua result"));
            }
            long allowed = toLong(result.get(0));
            long used = toLong(result.get(1));
            if (allowed == 1) {
                return RateLimitResult.allowed(used, memberId);
            }
            return RateLimitResult.blocked(used);
        } catch (Exception e) {
            return unavailable(e);
        }
    }

    @Override
    public void releaseRpm(long userId, String memberId) {
        if (memberId == null) {
            return;
        }
        try {
            redis.execute(releaseRpmScript, Collections.singletonList(rpmKey(userId)), memberId);
        } catch (Exception e) {
            meterRegistry.counter("ai_gateway_redis_errors_total", "op", "rpm_release").increment();
            log.warn("Redis RPM release failed: {}", e.toString());
        }
    }

    @Override
    public RateLimitResult peekRpd(long userId, int limitPerDay, String utcDate) {
        try {
            String key = rpdKey(userId, utcDate);
            String raw = redis.opsForValue().get(key);
            long used = raw == null ? 0 : Long.parseLong(raw);
            if (used >= limitPerDay) {
                return RateLimitResult.blocked(used);
            }
            return RateLimitResult.allowed(used, null);
        } catch (Exception e) {
            return unavailable(e);
        }
    }

    @Override
    public RateLimitResult tryAcquireRpd(long userId, int limitPerDay, String utcDate, int ttlSeconds) {
        try {
            List<?> result = redis.execute(
                    tryRpdScript,
                    Collections.singletonList(rpdKey(userId, utcDate)),
                    String.valueOf(limitPerDay),
                    String.valueOf(ttlSeconds)
            );
            if (result == null || result.size() < 2) {
                return unavailable(new IllegalStateException("empty rpd lua result"));
            }
            long allowed = toLong(result.get(0));
            long used = toLong(result.get(1));
            if (allowed == 1) {
                return RateLimitResult.allowed(used, null);
            }
            return RateLimitResult.blocked(used);
        } catch (Exception e) {
            return unavailable(e);
        }
    }

    @Override
    public void bootstrapRpdIfAbsent(long userId, String utcDate, long countFromSql, int ttlSeconds) {
        try {
            String key = rpdKey(userId, utcDate);
            Boolean set = redis.opsForValue().setIfAbsent(key, String.valueOf(Math.max(0, countFromSql)),
                    Duration.ofSeconds(ttlSeconds));
            if (Boolean.TRUE.equals(set)) {
                meterRegistry.counter("ai_gateway_bootstrap_sql_total", "kind", "rpd").increment();
            }
        } catch (Exception e) {
            meterRegistry.counter("ai_gateway_redis_errors_total", "op", "rpd_bootstrap").increment();
            log.warn("Redis RPD bootstrap failed: {}", e.toString());
        }
    }

    private RateLimitResult unavailable(Exception e) {
        meterRegistry.counter("ai_gateway_redis_errors_total", "op", "rate_limit").increment();
        log.warn("Redis rate-limit unavailable: {}", e.toString());
        return RateLimitResult.unavailable();
    }

    private static String rpmKey(long userId) {
        return "rl:rpm:" + userId;
    }

    private static String rpdKey(long userId, String utcDate) {
        return "rl:rpd:" + userId + ":" + utcDate;
    }

    private static long toLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(o));
    }
}
