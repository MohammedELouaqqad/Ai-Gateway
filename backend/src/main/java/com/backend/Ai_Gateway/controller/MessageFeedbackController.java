package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.MessageFeedback;
import com.backend.Ai_Gateway.enums.FeedbackRating;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.services.ChatService;
import com.backend.Ai_Gateway.services.MessageFeedbackService;
import com.backend.Ai_Gateway.services.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/messages/{messageId}")
public class MessageFeedbackController {

    private final MessageFeedbackService messageFeedbackService;
    private final ChatService chatService;
    private final UserService userService;

    public MessageFeedbackController(
            MessageFeedbackService messageFeedbackService,
            ChatService chatService,
            UserService userService
    ) {
        this.messageFeedbackService = messageFeedbackService;
        this.chatService = chatService;
        this.userService = userService;
    }

    /** Upsert thumbs for an assistant message owned by the current user. */
    @PostMapping("/feedback")
    public Map<String, Object> upsert(
            @PathVariable Long messageId,
            @Valid @RequestBody FeedbackRequest request
    ) {
        Long userId = userService.requireCurrentUserId();
        MessageFeedback saved = messageFeedbackService.upsert(userId, messageId, request.rating(), request.reason());
        return toBody(saved);
    }

    /** Undo / clear feedback (toggle off). Idempotent. */
    @DeleteMapping("/feedback")
    public Map<String, Object> clear(@PathVariable Long messageId) {
        Long userId = userService.requireCurrentUserId();
        messageFeedbackService.clear(userId, messageId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messageId", messageId);
        body.put("myFeedback", null);
        return body;
    }

    /**
     * Regenerate assistant reply. Records implicit DOWN (source=REGENERATED),
     * does not duplicate the user message, and marks the old reply as superseded.
     */
    @PostMapping("/regenerate")
    public ChatResponse regenerate(@PathVariable Long messageId) {
        Long userId = userService.requireCurrentUserId();
        return chatService.regenerateAssistant(userId, messageId);
    }

    private static Map<String, Object> toBody(MessageFeedback feedback) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", feedback.getId());
        body.put("messageId", feedback.getMessageId());
        body.put("rating", feedback.getRating().name());
        body.put("source", feedback.getSource() != null ? feedback.getSource().name() : null);
        body.put("reason", feedback.getReason());
        body.put("modelName", feedback.getModelName());
        body.put("updatedAt", feedback.getUpdatedAt());
        body.put("myFeedback", feedback.getRating().name());
        return body;
    }

    public record FeedbackRequest(
            @NotNull FeedbackRating rating,
            String reason
    ) {}
}
