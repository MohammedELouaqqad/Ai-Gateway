package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.GuardrailsProperties;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageGuardServiceTest {

    @Mock UsageLogRepository usageLogRepository;

    private UsageGuardService guard;
    private Model localModel;
    private Model cloudModel;

    @BeforeEach
    void setUp() {
        GuardrailsProperties props = new GuardrailsProperties();
        props.setEnabled(true);
        props.getRateLimit().setRequestsPerMinute(2);
        props.getRateLimit().setRequestsPerDay(100);
        props.getBudget().setUserPerDayEur(1.0);
        props.getBudget().setGlobalPerDayEur(10.0);
        GuardrailsProperties.ModelPrice price = new GuardrailsProperties.ModelPrice();
        price.setInputPer1kEur(1.0);
        price.setOutputPer1kEur(1.0);
        props.getPricing().put("gpt-4o-mini", price);

        ModelPricingService pricing = new ModelPricingService(props);
        guard = new UsageGuardService(props, usageLogRepository, pricing, new SimpleMeterRegistry());

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
                () -> guard.assertAllowed(1L, localModel, msgs)
        );
        assertEquals(GuardrailsExceededException.Reason.RATE_LIMIT_MINUTE, ex.getReason());
    }

    @Test
    void budget_skipsLocalModel() {
        guard.updateRuntimeConfig(true, 100, 100, 10.0, 0.0000001);
        assertDoesNotThrow(() -> guard.assertAllowed(1L, localModel, List.of(new ChatMessage(MessageRole.USER, "hi"))));
    }

    @Test
    void budget_blocksCloudWhenUserCapExceeded() {
        guard.updateRuntimeConfig(true, 100, 100, 50.0, 0.0001);
        when(usageLogRepository.sumEstimatedCostByUserIdAndCreatedAtBetween(eq(1L), any(), any())).thenReturn(0.0);
        String big = "x".repeat(8000);
        GuardrailsExceededException ex = assertThrows(
                GuardrailsExceededException.class,
                () -> guard.assertAllowed(1L, cloudModel, List.of(new ChatMessage(MessageRole.USER, big)))
        );
        assertEquals(GuardrailsExceededException.Reason.BUDGET_USER, ex.getReason());
    }
}
