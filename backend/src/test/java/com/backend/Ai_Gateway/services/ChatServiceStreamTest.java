package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.RetryProperties;
import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.enums.RequestStatus;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.UsageLogEntry;
import com.backend.Ai_Gateway.services.dto.UserChatRequest;
import com.backend.Ai_Gateway.services.guardrails.BudgetReservation;
import com.backend.Ai_Gateway.services.guardrails.GuardAdmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceStreamTest {

    @Mock UserRepository userRepository;
    @Mock ConversationRepository conversationRepository;
    @Mock ConversationService conversationService;
    @Mock MessageService messageService;
    @Mock ModelService modelService;
    @Mock UsageService usageService;
    @Mock UsageGuardService usageGuardService;
    @Mock LLMProviderRouter llmProviderRouter;
    @Mock MessageFeedbackService messageFeedbackService;
    @Mock AttachmentService attachmentService;
    @Mock com.backend.Ai_Gateway.services.rag.RagRetrievalService ragRetrievalService;

    private ChatService chatService;
    private Model model;
    private Provider provider;
    private BudgetReservation reservation;

    @BeforeEach
    void setUp() {
        reservation = new BudgetReservation(1L, "2026-07-27", 10, true);
        lenient().when(usageGuardService.assertAllowed(anyLong(), any(), any()))
                .thenReturn(new GuardAdmission(reservation));
        lenient().when(ragRetrievalService.retrieve(any(), any(), any())).thenReturn(List.of());
        ChatUsageSupport chatUsageSupport = new ChatUsageSupport(
                modelService,
                usageService,
                usageGuardService,
                new ChatExecutor(llmProviderRouter, new RetryProperties())
        );
        chatService = new ChatService(
                conversationRepository,
                userRepository,
                conversationService,
                messageService,
                modelService,
                usageGuardService,
                llmProviderRouter,
                messageFeedbackService,
                chatUsageSupport,
                attachmentService,
                ragRetrievalService
        );

        User user = new User();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        provider = new Provider();
        provider.setId(10L);
        provider.setType(ProviderType.OLLAMA);
        provider.setEnabled(true);

        model = new Model();
        model.setId(20L);
        model.setName("qwen");
        model.setProvider(provider);

        Conversation conversation = new Conversation();
        conversation.setId(30L);
        conversation.setUser(user);
        when(modelService.resolveActiveModel("qwen")).thenReturn(model);
        lenient().when(modelService.getById(20L)).thenReturn(model);
        when(conversationService.createConversation(1L)).thenReturn(conversation);
        Message userMsg = new Message();
        userMsg.setId(40L);
        userMsg.setRole(MessageRole.USER);
        when(messageService.createUserMessage(any(), eq(30L), eq(1L))).thenReturn(userMsg);
        when(messageService.getMessagesByConversationId(1L, 30L)).thenReturn(List.of(userMsg));
        lenient().when(messageService.createAssistantMessage(any(), eq(30L), eq(1L), any(), anyInt())).thenAnswer(inv -> {
            Message a = new Message();
            a.setId(50L);
            a.setRole(MessageRole.ASSISTANT);
            a.setContent(inv.getArgument(0));
            return a;
        });
    }

    @Test
    void handleChatStream_success_confirmsBudgetAndPersists() {
        when(llmProviderRouter.stream(eq(provider), any())).thenReturn(Flux.just(
                ChatChunk.delta("bon"),
                ChatChunk.delta("jour"),
                ChatChunk.completed("stop", ChatChunk.TokenUsage.of(3, 2))
        ));

        chatService.handleChatStream(1L, new UserChatRequest(null, "hi", "qwen", true, null));

        verify(usageGuardService, timeout(3000)).confirmBudgetReservation(reservation, model, 3, 2);
        verify(usageGuardService, never()).releaseBudgetReservation(reservation);
        verify(messageService, timeout(3000)).createAssistantMessage("bonjour", 30L, 1L, model, 0);
        ArgumentCaptor<UsageLogEntry> usage = ArgumentCaptor.forClass(UsageLogEntry.class);
        verify(usageService, timeout(3000)).logChatUsage(usage.capture());
        UsageLogEntry entry = usage.getValue();
        assertEquals(1L, entry.userId());
        assertEquals(20L, entry.modelId());
        assertEquals(3, entry.promptTokens());
        assertEquals(2, entry.completionTokens());
        assertEquals(RequestStatus.SUCCESS, entry.status());
    }

    @Test
    void handleChatStream_upstreamError_releasesBudget() {
        when(llmProviderRouter.stream(eq(provider), any()))
                .thenReturn(Flux.error(new RuntimeException("upstream down")));

        chatService.handleChatStream(1L, new UserChatRequest(null, "hi", "qwen", true, null));

        verify(usageGuardService, timeout(3000)).releaseBudgetReservation(reservation);
        verify(usageGuardService, never()).confirmBudgetReservation(any(), any(), anyInt(), anyInt());
        ArgumentCaptor<UsageLogEntry> usage = ArgumentCaptor.forClass(UsageLogEntry.class);
        verify(usageService, timeout(3000)).logChatUsage(usage.capture());
        UsageLogEntry entry = usage.getValue();
        assertEquals(1L, entry.userId());
        assertEquals(20L, entry.modelId());
        assertEquals(0, entry.promptTokens());
        assertEquals(0, entry.completionTokens());
        assertEquals(RequestStatus.ERROR, entry.status());
    }

    @Test
    void handleChatStream_usesSingleModel_neverCallsSyncChatOrSecondProvider() {
        when(llmProviderRouter.stream(eq(provider), any())).thenReturn(Flux.just(
                ChatChunk.delta("ok"),
                ChatChunk.completed("stop", ChatChunk.TokenUsage.of(1, 1))
        ));

        chatService.handleChatStream(1L, new UserChatRequest(null, "hi", "qwen", true, null));

        verify(llmProviderRouter, timeout(3000).times(1)).stream(eq(provider), any());
        verify(llmProviderRouter, never()).chat(any(), any());
        verify(usageGuardService, timeout(3000)).confirmBudgetReservation(reservation, model, 1, 1);
    }

    @Test
    void handleChatStream_errorAfterBytes_releasesBudgetOnce_neverConfirms() {
        when(llmProviderRouter.stream(eq(provider), any())).thenReturn(
                Flux.concat(
                        Flux.just(ChatChunk.delta("partial")),
                        Flux.error(new RuntimeException("mid-stream failure"))
                )
        );

        chatService.handleChatStream(1L, new UserChatRequest(null, "hi", "qwen", true, null));

        verify(usageGuardService, timeout(3000).times(1)).releaseBudgetReservation(reservation);
        verify(usageGuardService, never()).confirmBudgetReservation(any(), any(), anyInt(), anyInt());
        verify(messageService, never()).createAssistantMessage(any(), anyLong(), anyLong(), any(), anyInt());
        ArgumentCaptor<UsageLogEntry> usage = ArgumentCaptor.forClass(UsageLogEntry.class);
        verify(usageService, timeout(3000)).logChatUsage(usage.capture());
        assertEquals(RequestStatus.ERROR, usage.getValue().status());
        verify(llmProviderRouter, timeout(3000).times(1)).stream(eq(provider), any());
        verify(llmProviderRouter, never()).chat(any(), any());
    }
}
