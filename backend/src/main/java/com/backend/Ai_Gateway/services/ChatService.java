package com.backend.Ai_Gateway.services;


import com.backend.Ai_Gateway.config.ContentLimits;
import com.backend.Ai_Gateway.entity.Attachment;
import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.enums.FeedbackRating;
import com.backend.Ai_Gateway.enums.FeedbackSource;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.dto.ChatAttachment;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.UserChatRequest;
import com.backend.Ai_Gateway.services.guardrails.GuardAdmission;
import com.backend.Ai_Gateway.services.rag.RagRetrievalService;
import com.backend.Ai_Gateway.services.rag.dto.RagChunkHit;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
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
    private final UsageGuardService usageGuardService;
    private final LLMProviderRouter llmProviderRouter;
    private final MessageFeedbackService messageFeedbackService;
    private final ChatUsageSupport chatUsageSupport;
    private final AttachmentService attachmentService;
    private final RagRetrievalService ragRetrievalService;

    public ChatService(
            ConversationRepository conversationRepository,
            UserRepository userRepository,
            ConversationService conversationService,
            MessageService messageService,
            ModelService modelService,
            UsageGuardService usageGuardService,
            LLMProviderRouter llmProviderRouter,
            MessageFeedbackService messageFeedbackService,
            ChatUsageSupport chatUsageSupport,
            AttachmentService attachmentService,
            RagRetrievalService ragRetrievalService
    ) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.modelService = modelService;
        this.usageGuardService = usageGuardService;
        this.llmProviderRouter = llmProviderRouter;
        this.messageFeedbackService = messageFeedbackService;
        this.chatUsageSupport = chatUsageSupport;
        this.attachmentService = attachmentService;
        this.ragRetrievalService = ragRetrievalService;
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
        chatUsageSupport.ensureAdapterAvailable(model, false);

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
        // Regenerate replays existing history verbatim — no fresh RAG retrieval is performed,
        // so ragChunksUsed is honestly 0 here (not "same as before": the prior turn's retrieval
        // result isn't reused/re-labeled onto this new reply).
        PreparedChat prepared = new PreparedChat(model, conversation, request, 0);

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
        ChatUsageSupport.SyncRunResult run = chatUsageSupport.runSync(
                userId, prepared.model(), prepared.request().messages());
        persistSuccess(userId, prepared, run.attempt().response(), run.latencyMs());
        return run.attempt().response();
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
            chatUsageSupport.recordError(
                    userId, prepared.model().getId(), System.currentTimeMillis() - startedAt, "timeout");
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
        chatUsageSupport.recordError(
                userId, prepared.model().getId(), System.currentTimeMillis() - startedAt,
                error.getClass().getSimpleName());
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

        Attachment attachment = null;
        Long attachmentId = userChatRequest.getAttachmentId();
        if (attachmentId != null) {
            // Attachment path: resolve+validate the model BEFORE touching the message/attachment
            // — refuse clearly (400) rather than leaving a half-applied attachment behind a
            // rejected request. Costs one extra (cheap) resolveActiveModel call versus the
            // text-only path below, which keeps its original create-message-then-resolve-model
            // order untouched (behavior-preserving for the overwhelmingly common case).
            Model visionModel = modelService.resolveActiveModel(userChatRequest.getModel());
            if (!visionModel.isVisionCapable()) {
                throw new IllegalArgumentException(
                        "model: '" + visionModel.getName() + "' does not support image attachments"
                );
            }
            attachment = attachmentService.requireOwned(userId, attachmentId);
            attachmentService.markAttached(attachment);
            messageService.createUserMessage(content, conversation.getId(), userId, attachment);
        } else {
            messageService.createUserMessage(content, conversation.getId(), userId);
        }

        Model model = modelService.resolveActiveModel(userChatRequest.getModel());
        chatUsageSupport.ensureAdapterAvailable(model, streaming);
        attachModelToConversation(conversation, model);

        List<Message> messages = messageService.getMessagesByConversationId(userId, conversation.getId());
        List<ChatMessage> chatMessages = fitToContextBudget(messages);
        if (attachment != null) {
            chatMessages = withAttachmentOnLastMessage(chatMessages, attachment);
        }

        // RAG (Phase 2) — fails open (see RagRetrievalService javadoc): a Qdrant/embedding
        // hiccup silently degrades this turn to a plain chat rather than failing the request.
        List<RagChunkHit> ragHits = ragRetrievalService.retrieve(userId, conversation.getId(), content);
        String ragContext = RagRetrievalService.formatContext(ragHits);
        if (ragContext != null) {
            chatMessages = withRagContext(chatMessages, ragContext);
        }

        ChatRequest request = new ChatRequest(model.getName(), chatMessages);
        return new PreparedChat(model, conversation, request, ragHits.size());
    }

    /**
     * Inserts the labeled RAG context as its own {@code SYSTEM} message immediately before the
     * current user turn — kept structurally separate from both the system/history messages and
     * the user's own words, so the model can distinguish "untrusted document excerpt" from
     * "what the user actually asked" (see {@link RagRetrievalService#formatContext} for the
     * indirect-prompt-injection framing itself).
     */
    private List<ChatMessage> withRagContext(List<ChatMessage> messages, String context) {
        if (messages.isEmpty()) {
            return messages;
        }
        List<ChatMessage> copy = new ArrayList<>(messages);
        copy.add(copy.size() - 1, new ChatMessage(MessageRole.SYSTEM, context));
        return copy;
    }

    /**
     * Replaces the last (just-created) turn with an attachment-carrying copy — history
     * reconstruction ({@link #fitToContextBudget}) stays text-only on every reload, so a past
     * turn's image is NOT re-sent on later messages in the same conversation (cost/latency
     * tradeoff, documented on {@link ChatMessage#attachments()}).
     */
    private List<ChatMessage> withAttachmentOnLastMessage(List<ChatMessage> messages, Attachment attachment) {
        if (messages.isEmpty()) {
            return messages;
        }
        byte[] bytes = attachmentService.readBytes(attachment);
        String base64 = Base64.getEncoder().encodeToString(bytes);
        ChatAttachment chatAttachment = new ChatAttachment(attachment.getContentType(), base64);
        List<ChatMessage> copy = new ArrayList<>(messages);
        ChatMessage last = copy.get(copy.size() - 1);
        copy.set(copy.size() - 1, new ChatMessage(last.role(), last.content(), List.of(chatAttachment)));
        return copy;
    }

    private void persistSuccess(Long userId, PreparedChat prepared, ChatResponse response, long latencyMs) {
        messageService.createAssistantMessage(
                response.content(),
                prepared.conversation().getId(),
                userId,
                prepared.model(),
                prepared.ragChunksUsed()
        );
        chatUsageSupport.recordSuccess(userId, prepared.model(), response, latencyMs);
    }

    private record PreparedChat(Model model, Conversation conversation, ChatRequest request, int ragChunksUsed) {}

    public Conversation getOrCreateConversation(Long userId, Long conversationId) {
        if (conversationId != null) {
            if (!conversationService.verifyProprty(userId, conversationId)) {
                throw new UnauthorizedException("Vous n'êtes pas propriétaire de cette conversation");
            }
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RessourceNotFoundException("Conversation not found"));
            // Trashed conversations don't silently come back to life via a stray chat request —
            // the client must restore it explicitly first (POST /conversations/{id}/restore).
            if (conversation.getDeletedAt() != null) {
                throw new RessourceNotFoundException("Conversation not found");
            }
            return conversation;
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
