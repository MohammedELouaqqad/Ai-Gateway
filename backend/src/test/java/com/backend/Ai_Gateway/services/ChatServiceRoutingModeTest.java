package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.RoutingProperties;
import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.enums.RequestStatus;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.UserChatRequest;
import com.backend.Ai_Gateway.services.guardrails.GuardAdmission;
import com.backend.Ai_Gateway.services.routing.RoutingDecision;
import com.backend.Ai_Gateway.services.routing.RoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceRoutingModeTest {

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
    private Model defaultModel;
    private Model winnerModel;
    private Provider ollama;
    private Provider openai;

    @BeforeEach
    void setUp() {
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

        User user = new User();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ollama = provider(10L, "Ollama", ProviderType.OLLAMA);
        openai = provider(11L, "OpenAI", ProviderType.OPENAI);
        defaultModel = model(20L, "llama3.2:1b", ollama);
        winnerModel = model(21L, "gpt-4o-mini", openai);

        Conversation conversation = new Conversation();
        conversation.setId(30L);
        conversation.setUser(user);
        when(conversationService.createConversation(1L)).thenReturn(conversation);
        when(messageService.getMessagesByConversationId(1L, 30L)).thenReturn(List.of());
    }

    @Test
    void shadowMode_usesDefaultModelEvenWhenWinnerExists() {
        RoutingProperties cfg = new RoutingProperties();
        cfg.setEnabled(true);
        cfg.setMode(RoutingProperties.Mode.SHADOW);
        when(routingService.effectiveConfig()).thenReturn(cfg);
        when(routingService.decide(eq(1L), anyInt(), eq(false))).thenReturn(decision(
                RoutingProperties.Mode.SHADOW, "gpt-4o-mini", 21L));
        when(modelService.resolveActiveModel(null)).thenReturn(defaultModel);
        when(llmProviderRouter.chat(eq(ollama), any())).thenReturn(new ChatResponse("ok", 1, 1));

        chatService.handleChat(1L, new UserChatRequest(null, "hello auto", null, false));

        verify(llmProviderRouter).chat(eq(ollama), any());
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(usageService).logChatUsage(
                eq(1L), eq(20L), eq(1), eq(1), anyLong(), eq(RequestStatus.SUCCESS),
                reason.capture(), eq("SHADOW"), eq("ECONOMY"), eq("gpt-4o-mini"));
        assertThat(reason.getValue()).isEqualTo("shadow_default");
        verify(routingService).persistAndMeter(any(), eq(1L), eq("llama3.2:1b"), eq(true));
    }

    @Test
    void activeMode_usesWinnerModel() {
        RoutingProperties cfg = new RoutingProperties();
        cfg.setEnabled(true);
        cfg.setMode(RoutingProperties.Mode.ACTIVE);
        when(routingService.effectiveConfig()).thenReturn(cfg);
        when(routingService.decide(eq(1L), anyInt(), eq(false))).thenReturn(decision(
                RoutingProperties.Mode.ACTIVE, "gpt-4o-mini", 21L));
        when(routingService.resolveModelEntity("gpt-4o-mini")).thenReturn(winnerModel);
        when(routingService.rankedModels(any())).thenReturn(List.of(winnerModel, defaultModel));
        when(llmProviderRouter.chat(eq(openai), any())).thenReturn(new ChatResponse("winner", 2, 2));

        chatService.handleChat(1L, new UserChatRequest(null, "hello auto", null, false));

        verify(llmProviderRouter).chat(eq(openai), any());
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(usageService).logChatUsage(
                eq(1L), eq(21L), eq(2), eq(2), anyLong(), eq(RequestStatus.SUCCESS),
                reason.capture(), eq("ACTIVE"), eq("ECONOMY"), eq("gpt-4o-mini"));
        assertThat(reason.getValue()).isEqualTo("active_winner");
        verify(routingService).persistAndMeter(any(), eq(1L), eq("gpt-4o-mini"), eq(false));
    }

    private static RoutingDecision decision(RoutingProperties.Mode mode, String selected, Long id) {
        return new RoutingDecision(
                mode,
                RoutingProperties.Profile.ECONOMY,
                selected,
                id,
                "llama3.2:1b",
                0.01,
                0,
                100,
                List.of(new RoutingDecision.RankedCandidate(
                        id, selected, "OpenAI", 0.01, 50, 80, 70, 90, Map.of())),
                List.of(),
                Map.of()
        );
    }

    private static Provider provider(long id, String name, ProviderType type) {
        Provider p = new Provider();
        p.setId(id);
        p.setName(name);
        p.setType(type);
        p.setEnabled(true);
        return p;
    }

    private static Model model(long id, String name, Provider provider) {
        Model m = new Model();
        m.setId(id);
        m.setName(name);
        m.setDisplayName(name);
        m.setEnabled(true);
        m.setProvider(provider);
        return m;
    }
}
