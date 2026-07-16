package com.backend.Ai_Gateway.controller;


import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.services.ChatService;
import com.backend.Ai_Gateway.services.UserService;
import com.backend.Ai_Gateway.services.dto.UserChatRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final UserService userService;

    public ChatController(ChatService chatService, UserService userService) {
        this.chatService = chatService;
        this.userService = userService;
    }

    /** Chat non-streaming (existant). */
    @PostMapping
    public ChatResponse createChat(@Valid @RequestBody UserChatRequest userChatRequest) {
        Long userId = userService.requireCurrentUserId();
        return chatService.handleChat(userId, userChatRequest);
    }

    /**
     * Chat streaming SSE — chaque event {@code chunk} contient un {@code ChatChunk}.
     * Le mode {@code POST /api/chat} (JSON) reste disponible pour {@code stream:false}.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter createChatStream(
            @Valid @RequestBody UserChatRequest userChatRequest,
            HttpServletResponse response
    ) {
        // Anti-buffering for proxies (Vite / nginx) so chunks flush immediately
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");

        Long userId = userService.requireCurrentUserId();
        return chatService.handleChatStream(userId, userChatRequest);
    }
}
