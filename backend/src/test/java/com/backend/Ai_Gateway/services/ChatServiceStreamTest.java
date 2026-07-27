package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.RoutingProperties;
import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.enums.RequestStatus;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.UserChatRequest;
import com.backend.Ai_Gateway.services.guardrails.BudgetReservation;
import com.backend.Ai_Gateway.services.guardrails.GuardAdmission;
import com.backend.Ai_Gateway.services.routing.RoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
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
    @Mock RoutingService routingService;

    private ChatService chatService;
    private Model model;
    private Provider provider;
    private BudgetReservation reservation;

    @BeforeEach
    void setUp() {
        lenient().when(routingService.effectiveConfig()).thenReturn(new RoutingProperties());
        reservation = new BudgetReservation(1L, "2026-07-27", 10, true);
        lenient().when(usageGuardService.assertAllowed(anyLong(), any(), any()))
                .thenReturn(new GuardAdmission(reservation));
        chatService = new ChatService(
                conversationRepository,
                userRepository,
                conversationService,
                messageService,
                modelService,
                usageService,
                usageGuardService,
                llmProviderRouter,
                routingService
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
        when(conversationService.createConversation(1L)).thenReturn(conversation);
        when(messageService.getMessagesByConversationId(1L, 30L)).thenReturn(List.of());
    }

    @Test
    void handleChatStream_success_confirmsBudgetAndPersists() {
        when(llmProviderRouter.stream(eq(provider), any())).thenReturn(Flux.just(
                ChatChunk.delta("bon"),
                ChatChunk.delta("jour"),
                ChatChunk.completed("stop", ChatChunk.TokenUsage.of(3, 2))
        ));

        chatService.handleChatStream(1L, new UserChatRequest(null, "hi", "qwen", true));

        verify(usageGuardService, timeout(3000)).confirmBudgetReservation(reservation, model, 3, 2);
        verify(usageGuardService, never()).releaseBudgetReservation(reservation);
        verify(messageService, timeout(3000)).createAssistantMessage("bonjour", 30L, 1L, model);
        verify(usageService, timeout(3000)).logChatUsage(
                eq(1L), eq(20L), eq(3), eq(2), anyLong(), eq(RequestStatus.SUCCESS),
                eq("explicit"), eq(null), eq(null), eq(null));
    }

    @Test
    void handleChatStream_upstreamError_releasesBudget() {
        when(llmProviderRouter.stream(eq(provider), any()))
                .thenReturn(Flux.error(new RuntimeException("upstream down")));

        chatService.handleChatStream(1L, new UserChatRequest(null, "hi", "qwen", true));

        verify(usageGuardService, timeout(3000)).releaseBudgetReservation(reservation);
        verify(usageGuardService, never()).confirmBudgetReservation(any(), any(), anyInt(), anyInt());
        verify(usageService, timeout(3000)).logChatUsage(
                eq(1L), eq(20L), eq(0), eq(0), anyLong(), eq(RequestStatus.ERROR),
                eq("explicit"), eq(null), eq(null), eq(null));
    }
}
