package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.RetryProperties;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.enums.MessageRole;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatExecutorTest {

    @Mock LLMProviderRouter llmProviderRouter;

    private ChatExecutor chatExecutor;
    private Provider providerA;
    private Model modelA;
    private List<ChatMessage> messages;

    @BeforeEach
    void setUp() {
        RetryProperties retryProperties = new RetryProperties();
        retryProperties.setMaxRetries(1);
        retryProperties.setRetryBackoffMs(0);
        chatExecutor = new ChatExecutor(llmProviderRouter, retryProperties);

        providerA = provider(1L, ProviderType.OLLAMA);
        modelA = model(10L, "llama", providerA);
        messages = List.of(new ChatMessage(MessageRole.USER, "hi"));
    }

    @Test
    void execute_failThenRetryThenSucceed_onSameModel() {
        when(llmProviderRouter.chat(eq(providerA), any()))
                .thenThrow(new RuntimeException(new ConnectException("refused")))
                .thenReturn(new ChatResponse("ok", 1, 1));

        ChatExecutor.SuccessfulAttempt result = chatExecutor.execute(modelA, messages);

        assertThat(result.model()).isEqualTo(modelA);
        assertThat(result.response().content()).isEqualTo("ok");
        verify(llmProviderRouter, times(2)).chat(eq(providerA), any());
    }

    @Test
    void execute_rateLimit_noRetry_throwsImmediately() {
        // 429 classifies as FAIL directly — no retry, no second candidate to fall back to.
        WebClientResponseException tooMany = WebClientResponseException.create(
                429, "Too Many", null, null, null);
        when(llmProviderRouter.chat(eq(providerA), any())).thenThrow(tooMany);

        assertThatThrownBy(() -> chatExecutor.execute(modelA, messages))
                .isInstanceOf(ChatExecutionFailedException.class);
        verify(llmProviderRouter, times(1)).chat(eq(providerA), any());
    }

    @Test
    void execute_allRetriesFail_throwsWithFailedModelId() {
        when(llmProviderRouter.chat(eq(providerA), any()))
                .thenThrow(new RuntimeException(new ConnectException("down")));

        assertThatThrownBy(() -> chatExecutor.execute(modelA, messages))
                .isInstanceOf(ChatExecutionFailedException.class)
                .satisfies(ex -> {
                    ChatExecutionFailedException failed = (ChatExecutionFailedException) ex;
                    assertThat(failed.failedModelId()).isEqualTo(10L);
                });
        verify(llmProviderRouter, times(2)).chat(eq(providerA), any()); // initial + 1 retry
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
