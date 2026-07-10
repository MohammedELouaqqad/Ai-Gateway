package com.backend.Ai_Gateway.controller;


import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.services.ChatService;
import com.backend.Ai_Gateway.services.dto.UserChatRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse createChat(@Valid @RequestBody UserChatRequest userChatRequest) {
        Long userId = 1L;  // Via JWT/Keycloak
        return chatService.handleChat(userId, userChatRequest);
    }
}
