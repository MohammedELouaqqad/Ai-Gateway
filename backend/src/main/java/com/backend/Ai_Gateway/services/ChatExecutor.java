package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.services.routing.ProviderFailureAction;
import com.backend.Ai_Gateway.services.routing.ProviderFailureClassifier;
import com.backend.Ai_Gateway.services.routing.RoutingService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sync-only try-order + retry/fallback policy.
 * <p>
 * Streaming deliberately does <strong>not</strong> use this component: once SSE bytes
 * have been sent, multi-model fallback is forbidden (double-billing / corrupted stream).
 */
@Component
public class ChatExecutor {

    private final LLMProviderRouter llmProviderRouter;
    private final RoutingService routingService;

    public ChatExecutor(LLMProviderRouter llmProviderRouter, RoutingService routingService) {
        this.llmProviderRouter = llmProviderRouter;
        this.routingService = routingService;
    }

    /**
     * Attempts candidates in order with retry-same / fallback-next per
     * {@link ProviderFailureClassifier} (always with {@code anyBytesReceived=false}).
     *
     * @return first successful attempt
     * @throws ChatExecutionFailedException when all candidates are exhausted (cause = last error)
     */
    public SuccessfulAttempt execute(List<Model> tryOrder, List<ChatMessage> messages) {
        if (tryOrder == null || tryOrder.isEmpty()) {
            throw new IllegalStateException("No routable model available");
        }
        RuntimeException lastError = null;
        int maxRetries = routingService.effectiveConfig().getMaxRetries();
        long backoff = routingService.effectiveConfig().getRetryBackoffMs();

        for (int i = 0; i < tryOrder.size(); i++) {
            Model candidate = tryOrder.get(i);
            ChatRequest request = new ChatRequest(candidate.getName(), messages);

            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    ChatResponse response = llmProviderRouter.chat(candidate.getProvider(), request);
                    if (i > 0) {
                        routingService.meterFallback("fallback_after_failure");
                    }
                    return new SuccessfulAttempt(candidate, request, response, i, lastError);
                } catch (RuntimeException ex) {
                    lastError = ex;
                    ProviderFailureAction action = ProviderFailureClassifier.classify(ex, false);
                    if (action == ProviderFailureAction.RETRY_SAME && attempt < maxRetries) {
                        sleepQuietly(backoff * (attempt + 1));
                        continue;
                    }
                    if (action == ProviderFailureAction.FALLBACK_NEXT && i + 1 < tryOrder.size()) {
                        routingService.meterFallback(ProviderFailureClassifier.reasonLabel(action, ex));
                        break;
                    }
                    if (action == ProviderFailureAction.RETRY_SAME && i + 1 < tryOrder.size()) {
                        routingService.meterFallback(ProviderFailureClassifier.reasonLabel(action, ex));
                        break;
                    }
                    throw new ChatExecutionFailedException(candidate.getId(), ex);
                }
            }
        }

        if (lastError != null) {
            Long modelId = tryOrder.get(tryOrder.size() - 1).getId();
            throw new ChatExecutionFailedException(modelId, lastError);
        }
        throw new IllegalStateException("No routable model available");
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

    public record SuccessfulAttempt(
            Model model,
            ChatRequest request,
            ChatResponse response,
            int candidateIndex,
            RuntimeException priorError
    ) {
        public boolean fellBack() {
            return candidateIndex > 0;
        }

        public String fallbackRoutingReason() {
            return "fallback:" + (priorError != null
                    ? priorError.getClass().getSimpleName()
                    : "error");
        }
    }
}
