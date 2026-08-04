package com.backend.Ai_Gateway.controller.dto.v1;

import jakarta.validation.constraints.NotBlank;

public record V1ChatMessage(
        @NotBlank String role,
        @NotBlank String content
) {}
