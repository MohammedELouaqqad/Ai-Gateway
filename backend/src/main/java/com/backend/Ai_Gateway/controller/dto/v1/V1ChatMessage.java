package com.backend.Ai_Gateway.controller.dto.v1;

import com.backend.Ai_Gateway.config.ContentLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record V1ChatMessage(
        @NotBlank String role,
        @NotBlank @Size(max = ContentLimits.CHAT_MESSAGE_MAX) String content
) {}
