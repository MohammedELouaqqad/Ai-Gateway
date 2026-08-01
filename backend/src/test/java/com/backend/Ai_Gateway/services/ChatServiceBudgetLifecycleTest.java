package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.RoutingProperties;
import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.enums.RequestStatus;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.UsageLogEntry;
import com.backend.Ai_Gateway.services.dto.UserChatRequest;
import com.backend.Ai_Gateway.services.guardrails.BudgetReservation;
import com.backend.Ai_Gateway.services.guardrails.GuardAdmission;
import com.backend.Ai_Gateway.services.routing.RoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceBudgetLifecycleTest {

    @Mock UserRepository userRepository;
    @Mock ConversationRepository conversationRepository;
    @Mock ConversationService conversationService;
    @Mock MessageService messageService;
    @Mock ModelService modelService;
    @Mock UsageService usageService;
    @Mock UsageGuardService usageGuardService;
    @Mock LLMProviderRouter llmProviderRouter;
    @Mock RoutingService routingService;
    @Mock MessageFeedbackService messageFeedbackService;

    private ChatService chatService;
    private Model model;
    private Provider provider;

    @BeforeEach
    void setUp() {
        lenient().when(routingService.effectiveConfig()).thenReturn(new RoutingProperties());
        chatService = new ChatService(
                conversationRepository,
                userRepository,
                conversationService,
                messageService,
                modelService,
                usageService,
                usageGuardService,
                llmProviderRouter,
                routingService,
                messageFeedbackService
        );

        provider = new Provider();
        provider.setId(1L);
        provider.setType(ProviderType.OPENAI);
        provider.setEnabled(true);
        provider.setName("OpenAI");

        model = new Model();
        model.setId(2L);
        model.setName("gpt-4o-mini");
        model.setEnabled(true);
        model.setProvider(provider);

        User user = new User();
        user.setId(1L);
        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setUser(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(modelService.resolveActiveModel("gpt-4o-mini")).thenReturn(model);
        when(conversationService.createConversation(1L)).thenReturn(conversation);
        Message userMsg = new Message();
        userMsg.setId(40L);
        userMsg.setRole(MessageRole.USER);
        when(messageService.createUserMessage(any(), eq(10L), eq(1L))).thenReturn(userMsg);
        when(messageService.getMessagesByConversationId(1L, 10L)).thenReturn(List.of(userMsg));
        lenient().when(messageService.createAssistantMessage(any(), eq(10L), eq(1L), any())).thenAnswer(inv -> {
            Message a = new Message();
            a.setId(50L);
            a.setRole(MessageRole.ASSISTANT);
            a.setContent(inv.getArgument(0));
            return a;
        });
    }

    @Test
    void handleChat_llmFailure_releasesBudgetReservation() {
        BudgetReservation reservation = new BudgetReservation(1L, "2026-07-27", 50, true);
        when(usageGuardService.assertAllowed(eq(1L), eq(model), any()))
                .thenReturn(new GuardAdmission(reservation));
        when(llmProviderRouter.chat(eq(provider), any()))
                .thenThrow(new RuntimeException("upstream timeout"));

        assertThrows(RuntimeException.class, () ->
                chatService.handleChat(1L, new UserChatRequest(null, "ping", "gpt-4o-mini", false)));

        verify(usageGuardService).releaseBudgetReservation(reservation);
        verify(usageGuardService, never()).confirmBudgetReservation(any(), any(), anyInt(), anyInt());
        ArgumentCaptor<UsageLogEntry> usage = ArgumentCaptor.forClass(UsageLogEntry.class);
        verify(usageService).logChatUsage(usage.capture());
        UsageLogEntry entry = usage.getValue();
        assertEquals(1L, entry.userId());
        assertEquals(2L, entry.modelId());
        assertEquals(0, entry.promptTokens());
        assertEquals(0, entry.completionTokens());
        assertEquals(RequestStatus.ERROR, entry.status());
    }

    @Test
    void handleChat_success_confirmsBudgetReservation() {
        BudgetReservation reservation = new BudgetReservation(1L, "2026-07-27", 50, true);
        when(usageGuardService.assertAllowed(eq(1L), eq(model), any()))
                .thenReturn(new GuardAdmission(reservation));
        when(llmProviderRouter.chat(eq(provider), any()))
                .thenReturn(new ChatResponse("ok", 10, 5));

        chatService.handleChat(1L, new UserChatRequest(null, "ping", "gpt-4o-mini", false));

        verify(usageGuardService).confirmBudgetReservation(reservation, model, 10, 5);
        verify(usageGuardService, never()).releaseBudgetReservation(reservation);
    }
}
