package com.backend.Ai_Gateway.provider.dto;

public record ChatResponse (
        String content,
        Integer promptTokens,
        Integer completionTokens
) {}
