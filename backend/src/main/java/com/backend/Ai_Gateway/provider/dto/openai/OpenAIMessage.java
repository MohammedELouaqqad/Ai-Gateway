package com.backend.Ai_Gateway.provider.dto.openai;

public record OpenAIMessage(
        String role,
        String content
) {}
