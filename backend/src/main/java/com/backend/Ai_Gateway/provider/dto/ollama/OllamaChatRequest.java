package com.backend.Ai_Gateway.provider.dto.ollama;

import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;

import java.util.List;

public record OllamaChatRequest(
        String model,
        List<ChatMessage> messages,
        boolean stream
) {}
