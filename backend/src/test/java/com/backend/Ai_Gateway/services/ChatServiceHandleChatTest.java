package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.ContentLimits;
import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.enums.RequestStatus;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.UserChatRequest;
import com.backend.Ai_Gateway.services.guardrails.GuardAdmission;
import com.backend.Ai_Gateway.services.routing.RoutingService;
import com.backend.Ai_Gateway.config.RoutingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceHandleChatTest {

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

    @BeforeEach
    void setUp() {
        lenient().when(routingService.effectiveConfig()).thenReturn(new RoutingProperties());
        lenient().when(usageGuardService.assertAllowed(anyLong(), any(), any())).thenReturn(GuardAdmission.empty());
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
    }

    @Test
    void handleChat_success_persistsAssistantAndLogsUsage() {
        User user = new User();
        user.setId(1L);

        Provider provider = new Provider();
        provider.setId(10L);
        provider.setType(ProviderType.OLLAMA);
        provider.setEnabled(true);

        Model model = new Model();
        model.setId(20L);
        model.setName("qwen");
        model.setDisplayName("Qwen 0.5B");
        model.setEnabled(true);
        model.setProvider(provider);

        Conversation conversation = new Conversation();
        conversation.setId(30L);
        conversation.setUser(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(modelService.resolveActiveModel("qwen")).thenReturn(model);
        when(conversationService.createConversation(1L)).thenReturn(conversation);
        when(messageService.getMessagesByConversationId(1L, 30L)).thenReturn(List.of());
        when(llmProviderRouter.chat(eq(provider), any()))
                .thenReturn(new ChatResponse("bonjour", 3, 2));

        UserChatRequest request = new UserChatRequest(null, "hello", "qwen", false);
        ChatResponse response = chatService.handleChat(1L, request);

        assertEquals("bonjour", response.content());
        verify(messageService).createUserMessage("hello", 30L, 1L);
        verify(messageService).createAssistantMessage("bonjour", 30L, 1L, model);
        verify(usageService).logChatUsage(
                eq(1L), eq(20L), eq(3), eq(2), anyLong(), eq(RequestStatus.SUCCESS),
                eq("explicit"), eq(null), eq(null), eq(null));
    }

    @Test
    void handleChat_blankContent_rejected() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));

        UserChatRequest request = new UserChatRequest(null, "   ", "qwen", false);

        assertThrows(IllegalArgumentException.class, () -> chatService.handleChat(1L, request));
        verify(llmProviderRouter, never()).chat(any(), any());
        verify(usageService, never()).logChatUsage(
                anyLong(), anyLong(), any(), any(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void handleChat_oversizedContent_rejected() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));

        String huge = "a".repeat(ContentLimits.CHAT_MESSAGE_MAX + 1);
        UserChatRequest request = new UserChatRequest(null, huge, "qwen", false);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> chatService.handleChat(1L, request)
        );
        assertTrue(ex.getMessage().contains("max"));
        verify(llmProviderRouter, never()).chat(any(), any());
    }

    @Test
    void handleChat_providerFailure_logsErrorUsage() {
        User user = new User();
        user.setId(1L);

        Provider provider = new Provider();
        provider.setId(10L);
        provider.setType(ProviderType.OPENAI);

        Model model = new Model();
        model.setId(20L);
        model.setName("gpt-4o");
        model.setProvider(provider);

        Conversation conversation = new Conversation();
        conversation.setId(30L);
        conversation.setUser(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(modelService.resolveActiveModel("gpt-4o")).thenReturn(model);
        when(conversationService.createConversation(1L)).thenReturn(conversation);
        when(messageService.getMessagesByConversationId(1L, 30L)).thenReturn(List.of());
        when(llmProviderRouter.chat(eq(provider), any()))
                .thenThrow(new RuntimeException("upstream timeout"));

        UserChatRequest request = new UserChatRequest(null, "ping", "gpt-4o", false);

        assertThrows(RuntimeException.class, () -> chatService.handleChat(1L, request));
        verify(usageService).logChatUsage(
                eq(1L), eq(20L), eq(0), eq(0), anyLong(), eq(RequestStatus.ERROR),
                eq("explicit"), eq(null), eq(null), eq(null));
        verify(messageService, never()).createAssistantMessage(any(), anyLong(), anyLong(), any());
    }

    @Test
    void handleChat_unknownUser_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(
                RessourceNotFoundException.class,
                () -> chatService.handleChat(99L, new UserChatRequest(null, "hi", "qwen", false))
        );
    }
}
