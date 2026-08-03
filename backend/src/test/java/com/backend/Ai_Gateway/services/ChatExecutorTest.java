package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.RoutingProperties;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.services.routing.RoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatExecutorTest {

    @Mock LLMProviderRouter llmProviderRouter;
    @Mock RoutingService routingService;

    private ChatExecutor chatExecutor;
    private Provider providerA;
    private Provider providerB;
    private Model modelA;
    private Model modelB;
    private List<ChatMessage> messages;

    @BeforeEach
    void setUp() {
        RoutingProperties cfg = new RoutingProperties();
        cfg.setMaxRetries(1);
        cfg.setRetryBackoffMs(0);
        when(routingService.effectiveConfig()).thenReturn(cfg);
        chatExecutor = new ChatExecutor(llmProviderRouter, routingService);

        providerA = provider(1L, ProviderType.OLLAMA);
        providerB = provider(2L, ProviderType.OPENAI);
        modelA = model(10L, "llama", providerA);
        modelB = model(20L, "gpt", providerB);
        messages = List.of(new ChatMessage(MessageRole.USER, "hi"));
    }

    @Test
    void execute_failThenRetryThenSucceed_onSameModel() {
        when(llmProviderRouter.chat(eq(providerA), any()))
                .thenThrow(new RuntimeException(new ConnectException("refused")))
                .thenReturn(new ChatResponse("ok", 1, 1));

        ChatExecutor.SuccessfulAttempt result = chatExecutor.execute(List.of(modelA), messages);

        assertThat(result.model()).isEqualTo(modelA);
        assertThat(result.response().content()).isEqualTo("ok");
        assertThat(result.fellBack()).isFalse();
        verify(llmProviderRouter, times(2)).chat(eq(providerA), any());
        verify(routingService, never()).meterFallback(any());
    }

    @Test
    void execute_failFailThenFallbackSucceed_onNextModel() {
        // ConnectException → RETRY_SAME; after retries exhausted with another model → fallback
        when(llmProviderRouter.chat(eq(providerA), any()))
                .thenThrow(new RuntimeException(new ConnectException("refused")));
        when(llmProviderRouter.chat(eq(providerB), any()))
                .thenReturn(new ChatResponse("fallback-ok", 2, 2));

        ChatExecutor.SuccessfulAttempt result = chatExecutor.execute(List.of(modelA, modelB), messages);

        assertThat(result.model()).isEqualTo(modelB);
        assertThat(result.response().content()).isEqualTo("fallback-ok");
        assertThat(result.fellBack()).isTrue();
        assertThat(result.fallbackRoutingReason()).startsWith("fallback:");
        verify(llmProviderRouter, times(2)).chat(eq(providerA), any()); // initial + 1 retry
        verify(llmProviderRouter, times(1)).chat(eq(providerB), any());
        verify(routingService).meterFallback(org.mockito.ArgumentMatchers.contains("retry_same"));
        verify(routingService).meterFallback("fallback_after_failure");
    }

    @Test
    void execute_rateLimit_fallsBackWithoutRetryingSame() {
        WebClientResponseException tooMany = WebClientResponseException.create(
                429, "Too Many", null, null, null);
        when(llmProviderRouter.chat(eq(providerA), any())).thenThrow(tooMany);
        when(llmProviderRouter.chat(eq(providerB), any()))
                .thenReturn(new ChatResponse("next", 1, 1));

        ChatExecutor.SuccessfulAttempt result = chatExecutor.execute(List.of(modelA, modelB), messages);

        assertThat(result.model()).isEqualTo(modelB);
        verify(llmProviderRouter, times(1)).chat(eq(providerA), any());
        verify(routingService).meterFallback(org.mockito.ArgumentMatchers.contains("fallback_next"));
    }

    @Test
    void execute_allFail_throwsWithFailedModelId() {
        when(llmProviderRouter.chat(eq(providerA), any()))
                .thenThrow(new RuntimeException(new ConnectException("down")));

        assertThatThrownBy(() -> chatExecutor.execute(List.of(modelA), messages))
                .isInstanceOf(ChatExecutionFailedException.class)
                .satisfies(ex -> {
                    ChatExecutionFailedException failed = (ChatExecutionFailedException) ex;
                    assertThat(failed.failedModelId()).isEqualTo(10L);
                });
        verify(llmProviderRouter, times(2)).chat(eq(providerA), any());
    }

    private static Provider provider(long id, ProviderType type) {
        Provider p = new Provider();
        p.setId(id);
        p.setType(type);
        p.setEnabled(true);
        return p;
    }

    private static Model model(long id, String name, Provider provider) {
        Model m = new Model();
        m.setId(id);
        m.setName(name);
        m.setProvider(provider);
        m.setEnabled(true);
        return m;
    }
}
