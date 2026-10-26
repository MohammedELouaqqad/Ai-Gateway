package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.config.ApiKeyAuthenticationToken;
import com.backend.Ai_Gateway.controller.dto.v1.V1ChatCompletionRequest;
import com.backend.Ai_Gateway.controller.dto.v1.V1ChatCompletionResponse;
import com.backend.Ai_Gateway.services.V1ChatService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** OpenAI-compatible public chat surface — authenticated by API key ({@link ApiKeyAuthenticationToken}), not Keycloak JWT. */
@RestController
@RequestMapping("/v1/chat")
public class V1ChatController {

    private final V1ChatService v1ChatService;

    public V1ChatController(V1ChatService v1ChatService) {
        this.v1ChatService = v1ChatService;
    }

    /**
     * OpenAI convention: one URL, {@code stream:true/false} in the body picks JSON vs SSE
     * — not content negotiation, not a separate path (unlike the internal /api/chat[/stream]
     * split). Returns {@link V1ChatCompletionResponse} or {@link SseEmitter} accordingly.
     */
    @PostMapping(value = "/completions", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Object completions(@Valid @RequestBody V1ChatCompletionRequest request, HttpServletResponse response) {
        Long userId = currentUserId();
        if (request.isStreaming()) {
            return v1ChatService.chatStream(userId, request, response);
        }
        V1ChatService.SyncResult result = v1ChatService.chatSync(userId, request);
        return V1ChatCompletionResponse.of(
                result.model().getName(),
                result.response().content(),
                result.response().promptTokens() != null ? result.response().promptTokens() : 0,
                result.response().completionTokens() != null ? result.response().completionTokens() : 0
        );
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
