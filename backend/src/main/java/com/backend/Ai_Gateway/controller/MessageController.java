package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.enums.FeedbackRating;
import com.backend.Ai_Gateway.services.MessageFeedbackService;
import com.backend.Ai_Gateway.services.MessageService;
import com.backend.Ai_Gateway.services.UserService;
import com.backend.Ai_Gateway.services.dto.MessageView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

    private final MessageService messageService;
    private final MessageFeedbackService messageFeedbackService;
    private final UserService userService;

    public MessageController(
            MessageService messageService,
            MessageFeedbackService messageFeedbackService,
            UserService userService
    ) {
        this.messageService = messageService;
        this.messageFeedbackService = messageFeedbackService;
        this.userService = userService;
    }

    @GetMapping
    public List<MessageView> list(@PathVariable Long conversationId) {
        Long userId = userService.requireCurrentUserId();
        List<Message> messages = messageService.getMessagesByConversationId(userId, conversationId);
        List<Long> ids = messages.stream().map(Message::getId).toList();
        Map<Long, FeedbackRating> mine = messageFeedbackService.ratingsForUser(userId, ids);
        return messages.stream()
                .map(m -> MessageView.from(m, mine.get(m.getId())))
                .toList();
    }
}
