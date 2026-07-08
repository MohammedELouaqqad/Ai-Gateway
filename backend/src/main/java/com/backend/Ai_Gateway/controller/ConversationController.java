package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.services.ConversationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<Conversation> list() {
        Long userId = 1L;
        return conversationService.getConversationsByUserId(userId);
    }

    @PostMapping
    public Conversation create() {
        Long userId = 1L;
        return conversationService.createConversation(userId);
    }

    @PutMapping("/{id}")
    public Conversation rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long userId = 1L;
        return conversationService.renameConversation(userId, id, body.get("title"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long userId = 1L;
        conversationService.deleteConversation(userId, id);
        return ResponseEntity.noContent().build();
    }
}
