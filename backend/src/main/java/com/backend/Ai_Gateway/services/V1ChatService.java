package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.controller.dto.v1.V1ChatCompletionChunk;
import com.backend.Ai_Gateway.controller.dto.v1.V1ChatCompletionRequest;
import com.backend.Ai_Gateway.controller.dto.v1.V1ChatMessage;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.services.guardrails.GuardAdmission;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stateless OpenAI-compatible chat: no conversation/message persistence (the client owns
 * history via {@code messages}), no routing decision (client picks {@code model} directly,
 * matching the "explicit" branch {@link ChatService} already uses). Reuses the same engine
 * ({@link ChatExecutor}, {@link LLMProviderRouter}, guardrails, usage logging) as the
 * internal {@code /api/chat} surface — this is a second entry point, not a second pipeline.
 */
@Service
public class V1ChatService {

    private static final long STREAM_TIMEOUT_MS = 300_000L;

    private final ModelService modelService;
    private final LLMProviderRouter llmProviderRouter;
    private final UsageGuardService usageGuardService;
    private final ChatUsageSupport chatUsageSupport;

    public V1ChatService(
            ModelService modelService,
            LLMProviderRouter llmProviderRouter,
            UsageGuardService usageGuardService,
            ChatUsageSupport chatUsageSupport
    ) {
        this.modelService = modelService;
        this.llmProviderRouter = llmProviderRouter;
        this.usageGuardService = usageGuardService;
        this.chatUsageSupport = chatUsageSupport;
    }

    public record SyncResult(Model model, ChatResponse response) {}

    public SyncResult chatSync(Long userId, V1ChatCompletionRequest request) {
        Model model = modelService.resolveActiveModel(request.model());
        chatUsageSupport.ensureAdapterAvailable(model, false);
        List<ChatMessage> messages = toChatMessages(request.messages());

        ChatUsageSupport.SyncRunResult run = chatUsageSupport.runSync(userId, model, messages);
        chatUsageSupport.recordSuccess(userId, run.attempt().model(), run.attempt().response(), run.latencyMs());
        return new SyncResult(run.attempt().model(), run.attempt().response());
    }

    /**
     * Streaming: like {@link ChatService#handleChatStream}, no multi-model fallback once
     * bytes are sent. Emits raw OpenAI SSE frames (no {@code event:} line) directly.
     */
    public SseEmitter chatStream(Long userId, V1ChatCompletionRequest request, HttpServletResponse response) {
        Model model = modelService.resolveActiveModel(request.model());
        chatUsageSupport.ensureAdapterAvailable(model, true);
        List<ChatMessage> messages = toChatMessages(request.messages());

        GuardAdmission admission = usageGuardService.assertAllowed(userId, model, messages);
        long startedAt = System.currentTimeMillis();

        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");

        String id = "chatcmpl-" + UUID.randomUUID();
        long created = System.currentTimeMillis() / 1000;
        String modelName = model.getName();

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        StringBuilder fullContent = new StringBuilder();
        AtomicReference<ChatChunk> lastDoneChunk = new AtomicReference<>();
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        AtomicBoolean budgetDone = new AtomicBoolean(false);
        AtomicBoolean openingSent = new AtomicBoolean(false);

        Runnable releaseBudgetIfNeeded = () -> {
            if (budgetDone.compareAndSet(false, true)) {
                usageGuardService.releaseBudgetReservation(admission.budgetReservation());
            }
        };
        Runnable cancelUpstream = () -> {
            Disposable d = subscriptionRef.get();
            if (d != null && !d.isDisposed()) {
                d.dispose();
            }
        };

        emitter.onCompletion(cancelUpstream);
        emitter.onTimeout(() -> {
            cancelUpstream.run();
            releaseBudgetIfNeeded.run();
            chatUsageSupport.recordError(userId, model.getId(), System.currentTimeMillis() - startedAt, "timeout");
            emitter.complete();
        });
        emitter.onError(ex -> {
            cancelUpstream.run();
            releaseBudgetIfNeeded.run();
        });

        Disposable subscription = llmProviderRouter.stream(model.getProvider(), new com.backend.Ai_Gateway.provider.dto.ChatRequest(modelName, messages))
                .subscribe(
                        chunk -> {
                            try {
                                if (openingSent.compareAndSet(false, true)) {
                                    emitter.send(sseData(V1ChatCompletionChunk.opening(id, created, modelName)));
                                }
                                if (!chunk.done() && chunk.content() != null && !chunk.content().isEmpty()) {
                                    fullContent.append(chunk.content());
                                    emitter.send(sseData(V1ChatCompletionChunk.delta(id, created, modelName, chunk.content())));
                                }
                                if (chunk.done()) {
                                    lastDoneChunk.set(chunk);
                                }
                            } catch (IOException ex) {
                                cancelUpstream.run();
                                emitter.completeWithError(ex);
                            }
                        },
                        error -> {
                            releaseBudgetIfNeeded.run();
                            chatUsageSupport.recordError(userId, model.getId(), System.currentTimeMillis() - startedAt,
                                    error.getClass().getSimpleName());
                            emitter.completeWithError(error);
                        },
                        () -> completeStream(
                                emitter, admission, budgetDone, userId, model, startedAt,
                                fullContent, lastDoneChunk.get(), id, created, modelName
                        )
                );
        subscriptionRef.set(subscription);
        return emitter;
    }

    private void completeStream(
            SseEmitter emitter,
            GuardAdmission admission,
            AtomicBoolean budgetDone,
            Long userId,
            Model model,
            long startedAt,
            StringBuilder fullContent,
            ChatChunk done,
            String id,
            long created,
            String modelName
    ) {
        try {
            Integer promptTokens = done != null && done.usage() != null ? done.usage().promptTokens() : 0;
            Integer completionTokens = done != null && done.usage() != null ? done.usage().completionTokens() : 0;

            usageGuardService.confirmBudgetReservation(
                    admission.budgetReservation(), model,
                    promptTokens != null ? promptTokens : 0,
                    completionTokens != null ? completionTokens : 0
            );
            budgetDone.set(true);

            long latencyMs = System.currentTimeMillis() - startedAt;
            chatUsageSupport.recordSuccess(
                    userId, model, new ChatResponse(fullContent.toString(), promptTokens, completionTokens), latencyMs);

            emitter.send(sseData(V1ChatCompletionChunk.done(id, created, modelName)));
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (Exception ex) {
            if (budgetDone.compareAndSet(false, true)) {
                usageGuardService.releaseBudgetReservation(admission.budgetReservation());
            }
            chatUsageSupport.recordError(
                    userId, model.getId(), System.currentTimeMillis() - startedAt, ex.getClass().getSimpleName());
            emitter.completeWithError(ex);
        }
    }

    private static SseEmitter.SseEventBuilder sseData(V1ChatCompletionChunk chunk) {
        return SseEmitter.event().data(chunk, MediaType.APPLICATION_JSON);
    }

    private static List<ChatMessage> toChatMessages(List<V1ChatMessage> messages) {
        return messages.stream().map(V1ChatService::toChatMessage).toList();
    }

    private static ChatMessage toChatMessage(V1ChatMessage message) {
        MessageRole role = switch (message.role().toLowerCase()) {
            case "user" -> MessageRole.USER;
            case "assistant" -> MessageRole.ASSISTANT;
            case "system" -> MessageRole.SYSTEM;
            default -> throw new IllegalArgumentException("messages[].role: unsupported value '" + message.role() + "'");
        };
        return new ChatMessage(role, message.content());
    }
}
