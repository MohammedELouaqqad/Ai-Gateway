package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.services.MessageService;
import com.backend.Ai_Gateway.services.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

    private final MessageService messageService;
    private final UserService userService;

    public MessageController(MessageService messageService, UserService userService) {
        this.messageService = messageService;
        this.userService = userService;
    }

    @GetMapping
    public List<Message> list(@PathVariable Long conversationId) {
        return messageService.getMessagesByConversationId(userService.requireCurrentUserId(), conversationId);
    }
}
