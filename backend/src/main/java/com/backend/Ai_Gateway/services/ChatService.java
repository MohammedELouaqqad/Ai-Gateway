package com.backend.Ai_Gateway.services;


import com.backend.Ai_Gateway.config.ContentLimits;
import com.backend.Ai_Gateway.config.RoutingProperties;
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
import com.backend.Ai_Gateway.services.routing.ProviderFailureAction;
import com.backend.Ai_Gateway.services.routing.ProviderFailureClassifier;
import com.backend.Ai_Gateway.services.routing.RoutingDecision;
import com.backend.Ai_Gateway.services.routing.RoutingService;
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
    private final RoutingService routingService;
    private final MessageFeedbackService messageFeedbackService;

    public ChatService(
            ConversationRepository conversationRepository,
            UserRepository userRepository,
            ConversationService conversationService,
            MessageService messageService,
            ModelService modelService,
            UsageService usageService,
            UsageGuardService usageGuardService,
            LLMProviderRouter llmProviderRouter,
            RoutingService routingService,
            MessageFeedbackService messageFeedbackService
    ) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.modelService = modelService;
        this.usageService = usageService;
        this.usageGuardService = usageGuardService;
        this.llmProviderRouter = llmProviderRouter;
        this.routingService = routingService;
        this.messageFeedbackService = messageFeedbackService;
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
        PreparedChat prepared = new PreparedChat(
                model,
                conversation,
                request,
                null,
                "regenerate",
                null,
                null,
                null,
                null
        );

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

        List<Model> tryOrder = buildTryOrder(prepared);
        RuntimeException lastError = null;
        boolean budgetSettled = false;

        try {
            for (int i = 0; i < tryOrder.size(); i++) {
                Model candidate = tryOrder.get(i);
                ChatRequest request = new ChatRequest(candidate.getName(), prepared.request().messages());
                int maxRetries = routingService.effectiveConfig().getMaxRetries();
                long backoff = routingService.effectiveConfig().getRetryBackoffMs();

                for (int attempt = 0; attempt <= maxRetries; attempt++) {
                    try {
                        ChatResponse response = llmProviderRouter.chat(candidate.getProvider(), request);
                        PreparedChat used = prepared.withModel(candidate, request);
                        if (i > 0) {
                            routingService.meterFallback("fallback_after_failure");
                            used = used.withRoutingReason("fallback:" + (lastError != null
                                    ? lastError.getClass().getSimpleName()
                                    : "error"));
                        }
                        attachModelToConversation(used.conversation(), candidate);
                        usageGuardService.confirmBudgetReservation(
                                admission.budgetReservation(),
                                candidate,
                                response.promptTokens() != null ? response.promptTokens() : 0,
                                response.completionTokens() != null ? response.completionTokens() : 0
                        );
                        budgetSettled = true;
                        persistSuccess(userId, used, response, System.currentTimeMillis() - startedAt);
                        return response;
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
                        logError(userId, candidate.getId(), prepared, startedAt);
                        throw ex;
                    }
                }
            }

            if (lastError != null) {
                throw lastError;
            }
            throw new IllegalStateException("No routable model available");
        } finally {
            if (!budgetSettled) {
                usageGuardService.releaseBudgetReservation(admission.budgetReservation());
            }
        }
    }

    /**
     * Streaming SSE : chaque {@link ChatChunk} est relayé immédiatement.
     * Fallback multi-modèle non appliqué une fois des chunks envoyés (risque double facturation).
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

        Runnable releaseBudgetIfNeeded = () -> {
            if (budgetDone.compareAndSet(false, true)) {
                usageGuardService.releaseBudgetReservation(admission.budgetReservation());
            }
        };

        Runnable cancelUpstream = () -> {
            Disposable disposable = subscriptionRef.get();
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
        };

        emitter.onCompletion(cancelUpstream);
        emitter.onTimeout(() -> {
            cancelUpstream.run();
            releaseBudgetIfNeeded.run();
            logError(userId, prepared.model().getId(), prepared, startedAt);
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
                        error -> {
                            releaseBudgetIfNeeded.run();
                            logError(userId, prepared.model().getId(), prepared, startedAt);
                            emitter.completeWithError(error);
                        },
                        () -> {
                            try {
                                ChatChunk done = lastDoneChunk.get();
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
                                releaseBudgetIfNeeded.run();
                                logError(userId, prepared.model().getId(), prepared, startedAt);
                                emitter.completeWithError(ex);
                            }
                        }
                );
        subscriptionRef.set(subscription);
        return emitter;
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

        boolean explicitModel = userChatRequest.getModel() != null && !userChatRequest.getModel().isBlank();
        RoutingProperties routingCfg = routingService.effectiveConfig();
        RoutingDecision decision = null;
        Model model;
        String routingReason;
        String routingMode = null;
        String routingProfile = null;
        String routingSelectedModel = null;
        Long decisionLogId = null;

        Conversation conversation = getOrCreateConversation(userId, userChatRequest.getConversationId());
        Message userMessage = messageService.createUserMessage(content, conversation.getId(), userId);

        if (explicitModel || !routingCfg.isEnabled()) {
            model = modelService.resolveActiveModel(userChatRequest.getModel());
            routingReason = explicitModel ? "explicit" : "routing_disabled";
        } else {
            int promptChars = content.length();
            try {
                List<Message> existing = messageService.getMessagesByConversationId(userId, conversation.getId());
                for (Message m : existing) {
                    if (m.getId().equals(userMessage.getId())) {
                        continue;
                    }
                    if (m.getContent() != null) {
                        promptChars += m.getContent().length();
                    }
                }
            } catch (RuntimeException ignored) {
                // use content only
            }
            decision = routingService.decide(userId, promptChars, streaming);
            // Effective mode for logging: keep configured mode (SHADOW/CANARY/ACTIVE)
            routingMode = decision.mode().name();
            routingProfile = decision.profile().name();
            routingSelectedModel = decision.selectedModelName();

            boolean applyLive = routingService.shouldApplyLiveWinner(conversation.getId(), userId)
                    && decision.hasWinner();
            if (applyLive) {
                Model winner = routingService.resolveModelEntity(decision.selectedModelName());
                model = winner != null ? winner : modelService.resolveActiveModel(null);
                if (winner != null) {
                    routingReason = decision.mode() == RoutingProperties.Mode.CANARY
                            ? "canary_winner"
                            : "active_winner";
                } else {
                    routingReason = "active_fallback_default";
                }
            } else {
                model = modelService.resolveActiveModel(null);
                if (decision.mode() == RoutingProperties.Mode.CANARY) {
                    routingReason = "canary_holdout_default";
                } else {
                    routingReason = "shadow_default";
                }
            }

            boolean diverged = decision.hasWinner()
                    && decision.selectedModelName() != null
                    && !decision.selectedModelName().equals(model.getName());
            decisionLogId = routingService.persistAndMeter(
                    decision,
                    userId,
                    model.getName(),
                    diverged,
                    content,
                    conversation.getId(),
                    userMessage.getId()
            );
        }

        ensureAdapterAvailable(model, streaming);
        attachModelToConversation(conversation, model);

        List<Message> messages = messageService.getMessagesByConversationId(userId, conversation.getId());
        List<ChatMessage> chatMessages = fitToContextBudget(messages);
        ChatRequest request = new ChatRequest(model.getName(), chatMessages);
        return new PreparedChat(
                model,
                conversation,
                request,
                decision,
                routingReason,
                routingMode,
                routingProfile,
                routingSelectedModel,
                decisionLogId
        );
    }

    private List<Model> buildTryOrder(PreparedChat prepared) {
        RoutingProperties cfg = routingService.effectiveConfig();
        boolean liveReason = "active_winner".equals(prepared.routingReason())
                || "canary_winner".equals(prepared.routingReason());
        if (prepared.decision() == null
                || !liveReason
                || (cfg.getMode() != RoutingProperties.Mode.ACTIVE
                    && cfg.getMode() != RoutingProperties.Mode.CANARY)) {
            return List.of(prepared.model());
        }
        List<Model> ranked = routingService.rankedModels(prepared.decision());
        if (ranked.isEmpty()) {
            return List.of(prepared.model());
        }
        // Ensure current model is first if present
        List<Model> order = new ArrayList<>();
        order.add(prepared.model());
        for (Model m : ranked) {
            if (!m.getId().equals(prepared.model().getId())) {
                order.add(m);
            }
        }
        return order;
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

    /**
     * Logs ERROR usage for the model that failed (may differ from {@code prepared.model()}
     * after sync fallback attempts).
     */
    private void logError(Long userId, Long modelId, PreparedChat prepared, long startedAt) {
        usageService.logChatUsage(UsageLogEntry.error(
                userId,
                modelId,
                System.currentTimeMillis() - startedAt,
                prepared.routingReason(),
                prepared.routingMode(),
                prepared.routingProfile(),
                prepared.routingSelectedModel()
        ));
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
        Message assistant = messageService.createAssistantMessage(
                response.content(),
                prepared.conversation().getId(),
                userId,
                prepared.model()
        );
        routingService.attachAppliedResponse(
                prepared.decisionLogId(),
                assistant.getId(),
                response.content(),
                response.promptTokens(),
                response.completionTokens()
        );
        usageService.logChatUsage(UsageLogEntry.success(
                userId,
                prepared.model().getId(),
                response.promptTokens(),
                response.completionTokens(),
                latencyMs,
                prepared.routingReason(),
                prepared.routingMode(),
                prepared.routingProfile(),
                prepared.routingSelectedModel()
        ));
        usageGuardService.recordSuccess(
                userId,
                prepared.model(),
                response.promptTokens() != null ? response.promptTokens() : 0,
                response.completionTokens() != null ? response.completionTokens() : 0,
                latencyMs
        );
    }

    private record PreparedChat(
            Model model,
            Conversation conversation,
            ChatRequest request,
            RoutingDecision decision,
            String routingReason,
            String routingMode,
            String routingProfile,
            String routingSelectedModel,
            Long decisionLogId
    ) {
        PreparedChat withModel(Model next, ChatRequest nextRequest) {
            return new PreparedChat(
                    next, conversation, nextRequest, decision,
                    routingReason, routingMode, routingProfile, routingSelectedModel, decisionLogId
            );
        }

        PreparedChat withRoutingReason(String reason) {
            return new PreparedChat(
                    model, conversation, request, decision,
                    reason, routingMode, routingProfile, routingSelectedModel, decisionLogId
            );
        }
    }

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
