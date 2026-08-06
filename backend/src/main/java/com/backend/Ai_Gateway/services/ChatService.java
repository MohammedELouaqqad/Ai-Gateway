package com.backend.Ai_Gateway.services;


import com.backend.Ai_Gateway.config.ContentLimits;
import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.enums.FeedbackRating;
import com.backend.Ai_Gateway.enums.FeedbackSource;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.exceptions.ProviderNotImplementedException;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.ProviderCapabilities;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.UsageLogEntry;
import com.backend.Ai_Gateway.services.dto.UserChatRequest;
import com.backend.Ai_Gateway.services.guardrails.GuardAdmission;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * No automatic model choice here — the model is always either explicitly requested or the
 * configured default ({@link ModelService#resolveActiveModel}). The automatic-routing /
 * shadow-mode engine that used to sit in front of this was removed; this service no longer
 * has any notion of "candidates" — one request always targets exactly one model.
 */
@Service
public class ChatService {

    private static final long STREAM_TIMEOUT_MS = 300_000L;

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final ModelService modelService;
    private final UsageService usageService;
    private final UsageGuardService usageGuardService;
    private final LLMProviderRouter llmProviderRouter;
    private final MessageFeedbackService messageFeedbackService;
    private final ChatExecutor chatExecutor;

    public ChatService(
            ConversationRepository conversationRepository,
            UserRepository userRepository,
            ConversationService conversationService,
            MessageService messageService,
            ModelService modelService,
            UsageService usageService,
            UsageGuardService usageGuardService,
            LLMProviderRouter llmProviderRouter,
            MessageFeedbackService messageFeedbackService,
            ChatExecutor chatExecutor
    ) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.modelService = modelService;
        this.usageService = usageService;
        this.usageGuardService = usageGuardService;
        this.llmProviderRouter = llmProviderRouter;
        this.messageFeedbackService = messageFeedbackService;
        this.chatExecutor = chatExecutor;
    }

    public ChatResponse handleChat(Long userId, UserChatRequest userChatRequest) {
        PreparedChat prepared = prepareChat(userId, userChatRequest, false);
        return completeSyncChat(userId, prepared);
    }

    /**
     * Regenerate an assistant reply: marks the old message as regenerated, records an implicit
     * DOWN (source=REGENERATED), re-runs the LLM on history up to the preceding user message
     * (no duplicate user message), and links supersededBy.
     */
    public ChatResponse regenerateAssistant(Long userId, Long assistantMessageId) {
        Message oldAssistant = messageService.requireOwnedAssistantMessage(userId, assistantMessageId);
        if (oldAssistant.isRegenerated()) {
            throw new IllegalArgumentException("message: already regenerated");
        }
        Message userMessage = messageService.findPrecedingUserMessage(oldAssistant);
        Conversation conversation = oldAssistant.getConversation();

        messageFeedbackService.upsert(
                userId,
                assistantMessageId,
                FeedbackRating.DOWN,
                "regenerated",
                FeedbackSource.REGENERATED
        );

        Model model = oldAssistant.getModelName() != null && !oldAssistant.getModelName().isBlank()
                ? modelService.resolveActiveModel(oldAssistant.getModelName())
                : modelService.resolveActiveModel(null);
        ensureAdapterAvailable(model, false);

        List<Message> history = messageService.getMessagesByConversationId(userId, conversation.getId());
        List<Message> clipped = new ArrayList<>();
        for (Message m : history) {
            clipped.add(m);
            if (m.getId().equals(userMessage.getId())) {
                break;
            }
        }
        List<ChatMessage> chatMessages = fitToContextBudget(clipped);
        ChatRequest request = new ChatRequest(model.getName(), chatMessages);
        PreparedChat prepared = new PreparedChat(model, conversation, request);

        ChatResponse response = completeSyncChat(userId, prepared);
        // persistSuccess already created the new assistant — link supersession on the previous one
        Message newest = messageService.findLatestAssistantMessage(conversation.getId())
                .orElse(null);
        if (newest != null && !newest.getId().equals(oldAssistant.getId())) {
            messageService.markRegenerated(oldAssistant, newest.getId());
        }
        return response;
    }

    private ChatResponse completeSyncChat(Long userId, PreparedChat prepared) {
        GuardAdmission admission = usageGuardService.assertAllowed(
                userId, prepared.model(), prepared.request().messages());
        long startedAt = System.currentTimeMillis();
        boolean budgetSettled = false;

        try {
            ChatExecutor.SuccessfulAttempt ok = chatExecutor.execute(prepared.model(), prepared.request().messages());
            usageGuardService.confirmBudgetReservation(
                    admission.budgetReservation(),
                    ok.model(),
                    ok.response().promptTokens() != null ? ok.response().promptTokens() : 0,
                    ok.response().completionTokens() != null ? ok.response().completionTokens() : 0
            );
            budgetSettled = true;
            persistSuccess(userId, prepared, ok.response(), System.currentTimeMillis() - startedAt);
            return ok.response();
        } catch (ChatExecutionFailedException ex) {
            logError(userId, ex.failedModelId(), startedAt, ex.causeAsRuntime().getClass().getSimpleName());
            throw ex.causeAsRuntime();
        } finally {
            if (!budgetSettled) {
                usageGuardService.releaseBudgetReservation(admission.budgetReservation());
            }
        }
    }

    /**
     * Streaming SSE : chaque {@link ChatChunk} est relayé immédiatement.
     * Pas de retry une fois des chunks envoyés (risque double facturation).
     */
    public SseEmitter handleChatStream(Long userId, UserChatRequest userChatRequest) {
        PreparedChat prepared = prepareChat(userId, userChatRequest, true);
        GuardAdmission admission = usageGuardService.assertAllowed(
                userId, prepared.model(), prepared.request().messages());
        long startedAt = System.currentTimeMillis();

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        StringBuilder fullContent = new StringBuilder();
        AtomicReference<ChatChunk> lastDoneChunk = new AtomicReference<>();
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        AtomicBoolean anyBytes = new AtomicBoolean(false);
        AtomicBoolean budgetDone = new AtomicBoolean(false);

        Runnable releaseBudgetIfNeeded = onceBudgetReleaser(admission, budgetDone);
        Runnable cancelUpstream = upstreamCanceller(subscriptionRef);

        emitter.onCompletion(cancelUpstream);
        emitter.onTimeout(() -> {
            cancelUpstream.run();
            releaseBudgetIfNeeded.run();
            logError(userId, prepared.model().getId(), startedAt, "timeout");
            emitter.complete();
        });
        emitter.onError(ex -> {
            cancelUpstream.run();
            releaseBudgetIfNeeded.run();
        });

        var provider = prepared.model().getProvider();
        Disposable subscription = llmProviderRouter.stream(provider, prepared.request())
                .subscribe(
                        chunk -> {
                            try {
                                if (!chunk.done() && chunk.content() != null && !chunk.content().isEmpty()) {
                                    anyBytes.set(true);
                                    fullContent.append(chunk.content());
                                }
                                if (chunk.done()) {
                                    lastDoneChunk.set(chunk);
                                }
                                emitter.send(SseEmitter.event()
                                        .name("chunk")
                                        .data(chunk, MediaType.APPLICATION_JSON));
                            } catch (IOException ex) {
                                cancelUpstream.run();
                                emitter.completeWithError(ex);
                            }
                        },
                        error -> completeStreamWithError(
                                emitter, releaseBudgetIfNeeded, userId, prepared, startedAt, error),
                        () -> completeStreamSuccess(
                                emitter,
                                admission,
                                budgetDone,
                                releaseBudgetIfNeeded,
                                userId,
                                prepared,
                                startedAt,
                                fullContent,
                                lastDoneChunk.get()
                        )
                );
        subscriptionRef.set(subscription);
        return emitter;
    }

    private Runnable onceBudgetReleaser(GuardAdmission admission, AtomicBoolean budgetDone) {
        return () -> {
            if (budgetDone.compareAndSet(false, true)) {
                usageGuardService.releaseBudgetReservation(admission.budgetReservation());
            }
        };
    }

    private static Runnable upstreamCanceller(AtomicReference<Disposable> subscriptionRef) {
        return () -> {
            Disposable disposable = subscriptionRef.get();
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
        };
    }

    private void completeStreamWithError(
            SseEmitter emitter,
            Runnable releaseBudgetIfNeeded,
            Long userId,
            PreparedChat prepared,
            long startedAt,
            Throwable error
    ) {
        releaseBudgetIfNeeded.run();
        logError(userId, prepared.model().getId(), startedAt, error.getClass().getSimpleName());
        emitter.completeWithError(error);
    }

    private void completeStreamSuccess(
            SseEmitter emitter,
            GuardAdmission admission,
            AtomicBoolean budgetDone,
            Runnable releaseBudgetIfNeeded,
            Long userId,
            PreparedChat prepared,
            long startedAt,
            StringBuilder fullContent,
            ChatChunk done
    ) {
        try {
            Integer promptTokens = done != null && done.usage() != null
                    ? done.usage().promptTokens() : 0;
            Integer completionTokens = done != null && done.usage() != null
                    ? done.usage().completionTokens() : 0;

            ChatResponse response = new ChatResponse(
                    fullContent.toString(),
                    promptTokens,
                    completionTokens
            );
            usageGuardService.confirmBudgetReservation(
                    admission.budgetReservation(),
                    prepared.model(),
                    promptTokens != null ? promptTokens : 0,
                    completionTokens != null ? completionTokens : 0
            );
            budgetDone.set(true);
            persistSuccess(userId, prepared, response, System.currentTimeMillis() - startedAt);

            if (done == null) {
                emitter.send(SseEmitter.event()
                        .name("chunk")
                        .data(ChatChunk.completed(
                                "stop",
                                ChatChunk.TokenUsage.of(promptTokens, completionTokens)
                        ), MediaType.APPLICATION_JSON));
            }
            emitter.complete();
        } catch (Exception ex) {
            completeStreamWithError(emitter, releaseBudgetIfNeeded, userId, prepared, startedAt, ex);
        }
    }

    private PreparedChat prepareChat(Long userId, UserChatRequest userChatRequest, boolean streaming) {
        userRepository.findById(userId)
                .orElseThrow(() -> new RessourceNotFoundException("User not Found"));

        String content = userChatRequest.getContent() == null ? "" : userChatRequest.getContent().trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("content: must not be blank");
        }
        if (content.length() > ContentLimits.CHAT_MESSAGE_MAX) {
            throw new IllegalArgumentException(
                    "content: max " + ContentLimits.CHAT_MESSAGE_MAX + " characters (got " + content.length() + ")"
            );
        }

        Conversation conversation = getOrCreateConversation(userId, userChatRequest.getConversationId());
        messageService.createUserMessage(content, conversation.getId(), userId);

        Model model = modelService.resolveActiveModel(userChatRequest.getModel());
        ensureAdapterAvailable(model, streaming);
        attachModelToConversation(conversation, model);

        List<Message> messages = messageService.getMessagesByConversationId(userId, conversation.getId());
        List<ChatMessage> chatMessages = fitToContextBudget(messages);
        ChatRequest request = new ChatRequest(model.getName(), chatMessages);
        return new PreparedChat(model, conversation, request);
    }

    private void logError(Long userId, Long modelId, long startedAt, String reason) {
        usageService.logChatUsage(UsageLogEntry.error(userId, modelId, System.currentTimeMillis() - startedAt));
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

    /** Avant persistence du message user et avant tout SseEmitter. */
    private void ensureAdapterAvailable(Model model, boolean streaming) {
        ProviderType type = model.getProvider().getType();
        boolean ok = streaming
                ? ProviderCapabilities.hasStreamAdapter(type)
                : ProviderCapabilities.hasChatAdapter(type);
        if (!ok) {
            throw new ProviderNotImplementedException(type);
        }
    }

    private void persistSuccess(Long userId, PreparedChat prepared, ChatResponse response, long latencyMs) {
        messageService.createAssistantMessage(
                response.content(),
                prepared.conversation().getId(),
                userId,
                prepared.model()
        );
        usageService.logChatUsage(UsageLogEntry.success(
                userId,
                prepared.model().getId(),
                response.promptTokens(),
                response.completionTokens(),
                latencyMs
        ));
        usageGuardService.recordSuccess(
                userId,
                prepared.model(),
                response.promptTokens() != null ? response.promptTokens() : 0,
                response.completionTokens() != null ? response.completionTokens() : 0,
                latencyMs
        );
    }

    private record PreparedChat(Model model, Conversation conversation, ChatRequest request) {}

    public Conversation getOrCreateConversation(Long userId, Long conversationId) {
        if (conversationId != null) {
            if (!conversationService.verifyProprty(userId, conversationId)) {
                throw new UnauthorizedException("Vous n'êtes pas propriétaire de cette conversation");
            }
            return conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RessourceNotFoundException("Conversation not found"));
        }
        return conversationService.createConversation(userId);
    }

    /** Met à jour le modèle courant de la conversation (dernier modèle utilisé). */
    private void attachModelToConversation(Conversation conversation, Model model) {
        if (conversation.getModel() == null || !conversation.getModel().getId().equals(model.getId())) {
            conversation.setModel(model);
            conversation.setUpdatedAt(Instant.now());
            conversationRepository.save(conversation);
        }
    }

    /**
     * Garde les messages les plus récents dans un budget caractères pour limiter
     * le risque de dépasser la fenêtre de contexte du modèle.
     */
    public static List<ChatMessage> fitToContextBudget(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> selected = new ArrayList<>();
        int used = 0;

        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m.isRegenerated()) {
                continue; // superseded answers must not pollute context / judge baselines
            }
            String body = m.getContent() == null ? "" : m.getContent();
            int cost = body.length();
            if (!selected.isEmpty() && used + cost > ContentLimits.CHAT_CONTEXT_CHAR_BUDGET) {
                break;
            }
            // Toujours inclure au moins le dernier message (même s'il est long)
            if (selected.isEmpty() && cost > ContentLimits.CHAT_CONTEXT_CHAR_BUDGET) {
                body = body.substring(0, ContentLimits.CHAT_CONTEXT_CHAR_BUDGET);
                cost = body.length();
            }
            selected.add(new ChatMessage(m.getRole(), body));
            used += cost;
        }

        Collections.reverse(selected);
        return selected;
    }
}
