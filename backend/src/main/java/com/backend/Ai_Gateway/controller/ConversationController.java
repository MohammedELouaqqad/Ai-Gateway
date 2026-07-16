package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.services.ConversationService;
import com.backend.Ai_Gateway.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final UserService userService;

    public ConversationController(ConversationService conversationService, UserService userService) {
        this.conversationService = conversationService;
        this.userService = userService;
    }

    @GetMapping
    public List<Conversation> list() {
        return conversationService.getConversationsByUserId(userService.requireCurrentUserId());
    }

    @PostMapping
    public Conversation create() {
        return conversationService.createConversation(userService.requireCurrentUserId());
    }

    @PutMapping("/{id}")
    public Conversation rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return conversationService.renameConversation(userService.requireCurrentUserId(), id, body.get("title"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        conversationService.deleteConversation(userService.requireCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
