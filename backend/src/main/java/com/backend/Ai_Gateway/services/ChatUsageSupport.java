package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.exceptions.ProviderNotImplementedException;
import com.backend.Ai_Gateway.provider.ProviderCapabilities;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.services.dto.UsageLogEntry;
import com.backend.Ai_Gateway.services.guardrails.GuardAdmission;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Shared sync-path orchestration and usage/metric recording for {@link ChatService} and
 * {@link V1ChatService} — the internal and OpenAI-compatible entry points run the same
 * guardrails → provider-call → usage-accounting cycle and only differ in what they persist
 * on top (conversation/message history vs nothing) and in how they stream. Streaming keeps
 * its own emitter wiring in each service (SSE wire formats differ); only the terminal
 * success/error accounting is shared here.
 */
@Component
public class ChatUsageSupport {

    private final ModelService modelService;
    private final UsageService usageService;
    private final UsageGuardService usageGuardService;
    private final ChatExecutor chatExecutor;

    public ChatUsageSupport(
            ModelService modelService,
            UsageService usageService,
            UsageGuardService usageGuardService,
            ChatExecutor chatExecutor
    ) {
        this.modelService = modelService;
        this.usageService = usageService;
        this.usageGuardService = usageGuardService;
        this.chatExecutor = chatExecutor;
    }

    public record SyncRunResult(ChatExecutor.SuccessfulAttempt attempt, long latencyMs) {}

    /** Avant persistence de tout message/emitter. */
    public void ensureAdapterAvailable(Model model, boolean streaming) {
        ProviderType type = model.getProvider().getType();
        boolean ok = streaming
                ? ProviderCapabilities.hasStreamAdapter(type)
                : ProviderCapabilities.hasChatAdapter(type);
        if (!ok) {
            throw new ProviderNotImplementedException(type);
        }
    }

    /**
     * Full guardrails-admission → provider-execution → budget-settlement cycle for the
     * non-streaming path, identical between {@code /api/chat} and {@code /v1/chat/completions}.
     * The returned latency is measured up to budget confirmation only — it excludes whatever
     * the caller still persists on top (e.g. an assistant message), so callers must call
     * {@link #recordSuccess} themselves once that extra persistence is done. On failure, error
     * usage/metrics are recorded here (also identical between callers) and the original cause
     * is rethrown; the reservation is released either way.
     */
    public SyncRunResult runSync(Long userId, Model model, List<ChatMessage> messages) {
        GuardAdmission admission = usageGuardService.assertAllowed(userId, model, messages);
        long startedAt = System.currentTimeMillis();
        boolean budgetSettled = false;
        try {
            ChatExecutor.SuccessfulAttempt ok = chatExecutor.execute(model, messages);
            usageGuardService.confirmBudgetReservation(
                    admission.budgetReservation(),
                    ok.model(),
                    ok.response().promptTokens() != null ? ok.response().promptTokens() : 0,
                    ok.response().completionTokens() != null ? ok.response().completionTokens() : 0
            );
            budgetSettled = true;
            return new SyncRunResult(ok, System.currentTimeMillis() - startedAt);
        } catch (ChatExecutionFailedException ex) {
            recordError(userId, ex.failedModelId(), System.currentTimeMillis() - startedAt,
                    ex.causeAsRuntime().getClass().getSimpleName());
            throw ex.causeAsRuntime();
        } finally {
            if (!budgetSettled) {
                usageGuardService.releaseBudgetReservation(admission.budgetReservation());
            }
        }
    }

    public void recordSuccess(Long userId, Model model, ChatResponse response, long latencyMs) {
        usageService.logChatUsage(UsageLogEntry.success(
                userId, model.getId(), response.promptTokens(), response.completionTokens(), latencyMs
        ));
        usageGuardService.recordSuccess(
                userId, model,
                response.promptTokens() != null ? response.promptTokens() : 0,
                response.completionTokens() != null ? response.completionTokens() : 0,
                latencyMs
        );
    }

    public void recordError(Long userId, Long modelId, long latencyMs, String reason) {
        usageService.logChatUsage(UsageLogEntry.error(userId, modelId, latencyMs));
        recordProviderErrorMetric(modelId, reason);
    }

    /** Best-effort only — a metrics lookup must never break the actual error-reporting path. */
    private void recordProviderErrorMetric(Long modelId, String reason) {
        try {
            usageGuardService.recordFailure(modelService.getById(modelId), reason);
        } catch (RuntimeException ignored) {
            // metric only
        }
    }
}
