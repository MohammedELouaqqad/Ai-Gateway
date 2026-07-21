package com.backend.Ai_Gateway.services;


import com.backend.Ai_Gateway.config.ContentLimits;
import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.enums.RequestStatus;
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
import com.backend.Ai_Gateway.services.dto.UserChatRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    public ChatService(
            ConversationRepository conversationRepository,
            UserRepository userRepository,
            ConversationService conversationService,
            MessageService messageService,
            ModelService modelService,
            UsageService usageService,
            UsageGuardService usageGuardService,
            LLMProviderRouter llmProviderRouter
    ) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.modelService = modelService;
        this.usageService = usageService;
        this.usageGuardService = usageGuardService;
        this.llmProviderRouter = llmProviderRouter;
    }

    public ChatResponse handleChat(Long userId, UserChatRequest userChatRequest) {
        PreparedChat prepared = prepareChat(userId, userChatRequest, false);
        usageGuardService.assertAllowed(userId, prepared.model(), prepared.request().messages());
        long startedAt = System.currentTimeMillis();

        try {
            ChatResponse response = llmProviderRouter.chat(prepared.model().getProvider(), prepared.request());
            persistSuccess(userId, prepared, response, System.currentTimeMillis() - startedAt);
            return response;
        } catch (RuntimeException ex) {
            usageService.logChatUsage(
                    userId,
                    prepared.model().getId(),
                    0,
                    0,
                    System.currentTimeMillis() - startedAt,
                    RequestStatus.ERROR
            );
            throw ex;
        }
    }

    /**
     * Streaming SSE : chaque {@link ChatChunk} est relayé immédiatement.
     * L'adaptateur stream est validé dans {@link #prepareChat} avant tout
     * {@link SseEmitter} (sinon 501 JSON via GlobalExceptionHandler).
     */
    public SseEmitter handleChatStream(Long userId, UserChatRequest userChatRequest) {
        PreparedChat prepared = prepareChat(userId, userChatRequest, true);
        usageGuardService.assertAllowed(userId, prepared.model(), prepared.request().messages());
        long startedAt = System.currentTimeMillis();

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        StringBuilder fullContent = new StringBuilder();
        AtomicReference<ChatChunk> lastDoneChunk = new AtomicReference<>();
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();

        Runnable cancelUpstream = () -> {
            Disposable disposable = subscriptionRef.get();
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
        };

        emitter.onCompletion(cancelUpstream);
        emitter.onTimeout(() -> {
            cancelUpstream.run();
            usageService.logChatUsage(
                    userId,
                    prepared.model().getId(),
                    0,
                    0,
                    System.currentTimeMillis() - startedAt,
                    RequestStatus.ERROR
            );
            emitter.complete();
        });
        emitter.onError(ex -> cancelUpstream.run());

        var provider = prepared.model().getProvider();
        Disposable subscription = llmProviderRouter.stream(provider, prepared.request())
                .subscribe(
                        chunk -> {
                            try {
                                if (!chunk.done() && chunk.content() != null && !chunk.content().isEmpty()) {
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
                            usageService.logChatUsage(
                                    userId,
                                    prepared.model().getId(),
                                    0,
                                    0,
                                    System.currentTimeMillis() - startedAt,
                                    RequestStatus.ERROR
                            );
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
                                usageService.logChatUsage(
                                        userId,
                                        prepared.model().getId(),
                                        0,
                                        0,
                                        System.currentTimeMillis() - startedAt,
                                        RequestStatus.ERROR
                                );
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

        Model model = modelService.resolveActiveModel(userChatRequest.getModel());
        ensureAdapterAvailable(model, streaming);

        Conversation conversation = getOrCreateConversation(userId, userChatRequest.getConversationId());
        attachModelToConversation(conversation, model);

        messageService.createUserMessage(content, conversation.getId(), userId);

        List<Message> messages = messageService.getMessagesByConversationId(userId, conversation.getId());
        List<ChatMessage> chatMessages = fitToContextBudget(messages);
        ChatRequest request = new ChatRequest(model.getName(), chatMessages);
        return new PreparedChat(model, conversation, request);
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
        usageService.logChatUsage(
                userId,
                prepared.model().getId(),
                response.promptTokens(),
                response.completionTokens(),
                latencyMs,
                RequestStatus.SUCCESS
        );
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
    static List<ChatMessage> fitToContextBudget(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> selected = new ArrayList<>();
        int used = 0;

        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
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
