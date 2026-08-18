package com.backend.Ai_Gateway.services.guardrails.redis;

import com.backend.Ai_Gateway.services.guardrails.BudgetReservation;
import com.backend.Ai_Gateway.services.guardrails.BudgetResult;
import com.backend.Ai_Gateway.services.guardrails.RateLimitResult;
import com.backend.Ai_Gateway.services.guardrails.StoreOutcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration: real Redis 7 + Lua scripts (RPM shared / budget reserve).
 * Skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("dockerAvailable")
class RedisGuardStoresIT {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private RedisRateLimitStore rateLimitStore;
    private RedisBudgetStore budgetStore;

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();

        rateLimitStore = new RedisRateLimitStore(
                template,
                meters,
                load("redis/rpm_peek.lua", Long.class),
                load("redis/rpm_try_acquire.lua", List.class),
                load("redis/rpm_release.lua", Long.class),
                load("redis/rpd_try_acquire.lua", List.class)
        );
        budgetStore = new RedisBudgetStore(
                template,
                meters,
                load("redis/budget_reserve.lua", List.class),
                load("redis/budget_adjust.lua", Long.class)
        );
    }

    @Test
    void rpmIsSharedAcrossLogicalClients() {
        long userId = 42L;
        assertThat(rateLimitStore.tryAcquireRpm(userId, 2).outcome()).isEqualTo(StoreOutcome.ALLOWED);
        assertThat(rateLimitStore.tryAcquireRpm(userId, 2).outcome()).isEqualTo(StoreOutcome.ALLOWED);
        RateLimitResult third = rateLimitStore.tryAcquireRpm(userId, 2);
        assertThat(third.outcome()).isEqualTo(StoreOutcome.BLOCKED);
    }

    @Test
    void budgetReserveBlocksSecondCallerNearCap() {
        String day = "2026-07-27";
        BudgetResult first = budgetStore.tryReserve(7L, day, 80, 100, 1000, 3600, 0, 0);
        assertThat(first.outcome()).isEqualTo(StoreOutcome.ALLOWED);

        BudgetResult second = budgetStore.tryReserve(7L, day, 30, 100, 1000, 3600, 0, 0);
        assertThat(second.outcome()).isEqualTo(StoreOutcome.BLOCKED);
        assertThat(second.blockCode()).isEqualTo(1);
    }

    @Test
    void budgetReleaseFreesReservation() {
        String day = "2026-07-27";
        BudgetResult reserved = budgetStore.tryReserve(8L, day, 50, 100, 1000, 3600, 0, 0);
        BudgetReservation r = reserved.reservation();
        budgetStore.release(r);
        BudgetResult again = budgetStore.tryReserve(8L, day, 50, 100, 1000, 3600, 0, 0);
        assertThat(again.outcome()).isEqualTo(StoreOutcome.ALLOWED);
    }

    @Test
    void rpdBootstrapThenAcquire() {
        rateLimitStore.bootstrapRpdIfAbsent(9L, "2026-07-27", 2, 3600);
        RateLimitResult peek = rateLimitStore.peekRpd(9L, 3, "2026-07-27");
        assertThat(peek.outcome()).isEqualTo(StoreOutcome.ALLOWED);
        assertThat(rateLimitStore.tryAcquireRpd(9L, 3, "2026-07-27", 3600).outcome())
                .isEqualTo(StoreOutcome.ALLOWED);
        assertThat(rateLimitStore.tryAcquireRpd(9L, 3, "2026-07-27", 3600).outcome())
                .isEqualTo(StoreOutcome.BLOCKED);
    }

    private static <T> DefaultRedisScript<T> load(String path, Class<T> type) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(type);
        return script;
    }
}
