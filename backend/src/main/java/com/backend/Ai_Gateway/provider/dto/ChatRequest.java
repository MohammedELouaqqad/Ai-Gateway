package com.backend.Ai_Gateway.provider.dto;

import com.backend.Ai_Gateway.entity.Message;

import java.util.List;

public record ChatRequest (
    String model,
    List<ChatMessage> messages
) {}
