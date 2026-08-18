package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.RetryProperties;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sync-only retry policy for a single, already-resolved model — retries the SAME
 * provider on a transient failure (see {@link ProviderFailureClassifier}).
 * <p>
 * Streaming deliberately does <strong>not</strong> use this component: once SSE bytes
 * have been sent, a retry is forbidden (double-billing / corrupted stream).
 */
@Component
public class ChatExecutor {

    private final LLMProviderRouter llmProviderRouter;
    private final RetryProperties retryProperties;

    public ChatExecutor(LLMProviderRouter llmProviderRouter, RetryProperties retryProperties) {
        this.llmProviderRouter = llmProviderRouter;
        this.retryProperties = retryProperties;
    }

    /**
     * @return the successful attempt
     * @throws ChatExecutionFailedException once retries are exhausted (cause = last error)
     */
    public SuccessfulAttempt execute(Model model, List<ChatMessage> messages) {
        ChatRequest request = new ChatRequest(model.getName(), messages);
        int maxRetries = retryProperties.getMaxRetries();
        long backoff = retryProperties.getRetryBackoffMs();
        RuntimeException lastError = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                ChatResponse response = llmProviderRouter.chat(model.getProvider(), request);
                return new SuccessfulAttempt(model, request, response);
            } catch (RuntimeException ex) {
                lastError = ex;
                ProviderFailureAction action = ProviderFailureClassifier.classify(ex, false);
                if (action == ProviderFailureAction.RETRY_SAME && attempt < maxRetries) {
                    sleepQuietly(backoff * (attempt + 1));
                    continue;
                }
                throw new ChatExecutionFailedException(model.getId(), ex);
            }
        }
        // Unreachable (loop always returns or throws) — keeps the compiler happy.
        throw new ChatExecutionFailedException(model.getId(), lastError);
    }

    private static void sleepQuietly(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record SuccessfulAttempt(Model model, ChatRequest request, ChatResponse response) {}
}
