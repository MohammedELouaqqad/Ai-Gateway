package com.backend.Ai_Gateway.provider.dto;

import com.backend.Ai_Gateway.enums.MessageRole;

public record ChatMessage (
    MessageRole role,
    String content
) {}
