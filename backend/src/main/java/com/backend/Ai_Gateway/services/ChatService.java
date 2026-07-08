package com.backend.Ai_Gateway.services;


import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.enums.RequestStatus;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.provider.OllamaProvider;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.UserChatRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final ModelService modelService;
    private final UsageService usageService;
    private final OllamaProvider ollamaProvider;

    public ChatService(
            ConversationRepository conversationRepository,
            UserRepository userRepository,
            ConversationService conversationService,
            MessageService messageService,
            ModelService modelService,
            UsageService usageService,
            OllamaProvider ollamaProvider
    ) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.modelService = modelService;
        this.usageService = usageService;
        this.ollamaProvider = ollamaProvider;
    }

    public ChatResponse handleChat(Long userId, UserChatRequest userChatRequest) {
        userRepository.findById(userId)
                .orElseThrow(() -> new RessourceNotFoundException("User not Found"));

        Model model = modelService.resolveActiveModel(userChatRequest.getModel());
        Conversation conversation = getOrCreateConversation(userId, userChatRequest.getConversationId());
        attachModelToConversation(conversation, model);

        messageService.createUserMessage(userChatRequest.getContent(), conversation.getId(), userId);

        List<Message> messages = messageService.getMessagesByConversationId(userId, conversation.getId());
        List<ChatMessage> chatMessages = messages.stream()
                .map(m -> new ChatMessage(m.getRole(), m.getContent()))
                .toList();

        ChatRequest request = new ChatRequest(model.getName(), chatMessages);
        long startedAt = System.currentTimeMillis();

        try {
            ChatResponse response = ollamaProvider.chat(request);
            messageService.createAssistantMessage(response.content(), conversation.getId(), userId);
            usageService.logChatUsage(
                    userId,
                    model.getId(),
                    response.promptTokens(),
                    response.completionTokens(),
                    System.currentTimeMillis() - startedAt,
                    RequestStatus.SUCCESS
            );
            return response;
        } catch (RuntimeException ex) {
            usageService.logChatUsage(
                    userId,
                    model.getId(),
                    0,
                    0,
                    System.currentTimeMillis() - startedAt,
                    RequestStatus.ERROR
            );
            throw ex;
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

    private void attachModelToConversation(Conversation conversation, Model model) {
        if (conversation.getModel() == null) {
            conversation.setModel(model);
            conversation.setUpdatedAt(Instant.now());
            conversationRepository.save(conversation);
        }
    }
}
