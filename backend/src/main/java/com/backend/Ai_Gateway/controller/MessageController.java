package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.services.MessageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public List<Message> list(@PathVariable Long conversationId) {
        Long userId = 1L;
        return messageService.getMessagesByConversationId(userId, conversationId);
    }
}
