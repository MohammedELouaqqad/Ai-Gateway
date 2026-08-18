package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.GuardrailsProperties;
import com.backend.Ai_Gateway.config.RedisGuardProperties;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException;
import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException.Reason;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import com.backend.Ai_Gateway.services.guardrails.BudgetReservation;
import com.backend.Ai_Gateway.services.guardrails.GuardAdmission;
import com.backend.Ai_Gateway.services.guardrails.RateLimitResult;
import com.backend.Ai_Gateway.services.guardrails.RateLimitStore;
import com.backend.Ai_Gateway.services.guardrails.memory.InMemoryBudgetStore;
import com.backend.Ai_Gateway.services.guardrails.memory.InMemoryRateLimitStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageGuardServiceTest {

    @Mock UsageLogRepository usageLogRepository;

    private UsageGuardService guard;
    private InMemoryBudgetStore budgetStore;
    private Model localModel;
    private Model cloudModel;

    @BeforeEach
    void setUp() {
        GuardrailsProperties props = new GuardrailsProperties();
        props.setEnabled(true);
        props.getRateLimit().setRequestsPerMinute(2);
        props.getRateLimit().setRequestsPerDay(3);
        props.getBudget().setUserPerDayEur(1.0);
        props.getBudget().setGlobalPerDayEur(10.0);
        GuardrailsProperties.ModelPrice price = new GuardrailsProperties.ModelPrice();
        price.setInputPer1kEur(1.0);
        price.setOutputPer1kEur(1.0);
        props.getPricing().put("gpt-4o-mini", price);

        ModelPricingService pricing = new ModelPricingService(props);
        budgetStore = new InMemoryBudgetStore();
        guard = new UsageGuardService(
                props,
                usageLogRepository,
                pricing,
                new SimpleMeterRegistry(),
                new InMemoryRateLimitStore(),
                budgetStore,
                new RedisGuardProperties()
        );

        Provider ollama = new Provider();
        ollama.setName("Ollama");
        ollama.setType(ProviderType.OLLAMA);
        ollama.setLocation(ProviderLocation.LOCAL);
        localModel = new Model();
        localModel.setName("llama3.2:1b");
        localModel.setProvider(ollama);

        Provider openai = new Provider();
        openai.setName("OpenAI");
        openai.setType(ProviderType.OPENAI);
        openai.setLocation(ProviderLocation.CLOUD);
        cloudModel = new Model();
        cloudModel.setName("gpt-4o-mini");
        cloudModel.setProvider(openai);

        lenient().when(usageLogRepository.countByUserIdAndCreatedAtBetween(eq(1L), any(), any())).thenReturn(0L);
        lenient().when(usageLogRepository.sumEstimatedCostByUserIdAndCreatedAtBetween(eq(1L), any(), any())).thenReturn(0.0);
        lenient().when(usageLogRepository.sumEstimatedCostBetween(any(), any())).thenReturn(0.0);
    }

    @Test
    void rateLimitMinute_blocksThirdCall() {
        List<ChatMessage> msgs = List.of(new ChatMessage(MessageRole.USER, "hi"));
        assertDoesNotThrow(() -> guard.assertAllowed(1L, localModel, msgs));
        assertDoesNotThrow(() -> guard.assertAllowed(1L, localModel, msgs));
        GuardrailsExceededException ex = assertThrows(
                GuardrailsExceededException.class,
                () -> guard.assertAllowed(1L, localModel, msgs));
        assertThat(ex.getReason()).isEqualTo(Reason.RATE_LIMIT_MINUTE);
    }

    @Test
    void rateLimitDay_blocksWhenRpdExceeded() {
        lenient().when(usageLogRepository.countByUserIdAndCreatedAtBetween(eq(1L), any(), any())).thenReturn(3L);
        // bootstrap seeds RPD at 3; peek blocks before commit
        GuardrailsExceededException ex = assertThrows(
                GuardrailsExceededException.class,
                () -> guard.assertAllowed(1L, localModel, List.of(new ChatMessage(MessageRole.USER, "hi"))));
        assertThat(ex.getReason()).isEqualTo(Reason.RATE_LIMIT_DAY);
    }

    @Test
    void budgetBlock_doesNotConsumeRpm_d1() {
        lenient().when(usageLogRepository.sumEstimatedCostByUserIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(0.99);
        List<ChatMessage> msgs = List.of(new ChatMessage(MessageRole.USER, "x".repeat(4000)));

        GuardrailsExceededException ex = assertThrows(
                GuardrailsExceededException.class,
                () -> guard.assertAllowed(1L, cloudModel, msgs));
        assertThat(ex.getReason()).isIn(Reason.BUDGET_USER, Reason.BUDGET_GLOBAL);

        assertDoesNotThrow(() -> guard.assertAllowed(1L, localModel, List.of(new ChatMessage(MessageRole.USER, "a"))));
        assertDoesNotThrow(() -> guard.assertAllowed(1L, localModel, List.of(new ChatMessage(MessageRole.USER, "b"))));
    }

    @Test
    void cloudAdmit_returnsActiveBudgetReservation() {
        GuardAdmission admission = guard.assertAllowed(
                1L, cloudModel, List.of(new ChatMessage(MessageRole.USER, "hello")));
        assertThat(admission.budgetReservation().active()).isTrue();
        assertThat(admission.budgetReservation().estimatedCents()).isPositive();
    }

    @Test
    void releaseAndConfirmBudget_adjustStore() {
        GuardAdmission admission = guard.assertAllowed(
                1L, cloudModel, List.of(new ChatMessage(MessageRole.USER, "hello")));
        BudgetReservation r = admission.budgetReservation();
        long afterReserve = budgetStore.peekUserCents(1L, r.utcDate());
        assertThat(afterReserve).isEqualTo(r.estimatedCents());

        guard.releaseBudgetReservation(r);
        assertThat(budgetStore.peekUserCents(1L, r.utcDate())).isZero();

        GuardAdmission again = guard.assertAllowed(
                1L, cloudModel, List.of(new ChatMessage(MessageRole.USER, "hello2")));
        BudgetReservation r2 = again.budgetReservation();
        guard.confirmBudgetReservation(r2, cloudModel, 0, 0); // tokens missing → keep estimate
        assertThat(budgetStore.peekUserCents(1L, r2.utcDate())).isEqualTo(r2.estimatedCents());
    }

    @Test
    void ollama_skipsBudgetReservation() {
        GuardAdmission admission = guard.assertAllowed(
                1L, localModel, List.of(new ChatMessage(MessageRole.USER, "hi")));
        assertThat(admission.budgetReservation().active()).isFalse();
    }

    @Test
    void redisUnavailable_failClosed_blocksWith429Reason() {
        RedisGuardProperties redis = new RedisGuardProperties();
        redis.getRateLimit().setFailOpen(false);
        RateLimitStore unavailable = unavailableRateLimitStore();
        UsageGuardService failClosed = newGuard(unavailable, budgetStore, redis);

        GuardrailsExceededException ex = assertThrows(
                GuardrailsExceededException.class,
                () -> failClosed.assertAllowed(1L, localModel, List.of(new ChatMessage(MessageRole.USER, "hi"))));
        assertThat(ex.getReason()).isEqualTo(Reason.RATE_LIMIT_MINUTE);
    }

    @Test
    void redisUnavailable_failOpen_allowsRequest() {
        RedisGuardProperties redis = new RedisGuardProperties();
        redis.getRateLimit().setFailOpen(true);
        RateLimitStore unavailable = unavailableRateLimitStore();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        UsageGuardService failOpen = new UsageGuardService(
                defaultsProps(),
                usageLogRepository,
                new ModelPricingService(defaultsProps()),
                meters,
                unavailable,
                budgetStore,
                redis
        );

        assertDoesNotThrow(() -> failOpen.assertAllowed(
                1L, localModel, List.of(new ChatMessage(MessageRole.USER, "hi"))));
        assertThat(meters.counter("ai_gateway_redis_fail_open_total", "reason", "rate_limit_minute").count())
                .isPositive();
    }

    @Test
    void reconcileGlobalBudgetFromSql_writesSqlSpendToStore() {
        when(usageLogRepository.sumEstimatedCostBetween(any(), any())).thenReturn(1.25);
        guard.reconcileGlobalBudgetFromSql();
        String utcDate = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        assertThat(budgetStore.peekGlobalCents(utcDate)).isEqualTo(125L);
    }

    private UsageGuardService newGuard(RateLimitStore rateLimitStore, InMemoryBudgetStore budgets, RedisGuardProperties redis) {
        GuardrailsProperties props = defaultsProps();
        return new UsageGuardService(
                props,
                usageLogRepository,
                new ModelPricingService(props),
                new SimpleMeterRegistry(),
                rateLimitStore,
                budgets,
                redis
        );
    }

    private GuardrailsProperties defaultsProps() {
        GuardrailsProperties props = new GuardrailsProperties();
        props.setEnabled(true);
        props.getRateLimit().setRequestsPerMinute(2);
        props.getRateLimit().setRequestsPerDay(3);
        props.getBudget().setUserPerDayEur(1.0);
        props.getBudget().setGlobalPerDayEur(10.0);
        GuardrailsProperties.ModelPrice price = new GuardrailsProperties.ModelPrice();
        price.setInputPer1kEur(1.0);
        price.setOutputPer1kEur(1.0);
        props.getPricing().put("gpt-4o-mini", price);
        return props;
    }

    private static RateLimitStore unavailableRateLimitStore() {
        RateLimitStore store = mock(RateLimitStore.class);
        lenient().when(store.peekRpm(anyLong(), anyInt())).thenReturn(RateLimitResult.unavailable());
        lenient().when(store.tryAcquireRpm(anyLong(), anyInt())).thenReturn(RateLimitResult.unavailable());
        lenient().when(store.peekRpd(anyLong(), anyInt(), anyString())).thenReturn(RateLimitResult.unavailable());
        lenient().when(store.tryAcquireRpd(anyLong(), anyInt(), anyString(), anyInt())).thenReturn(RateLimitResult.unavailable());
        return store;
    }
}
