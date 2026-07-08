package com.backend.Ai_Gateway.provider.dto.ollama;

public record OllamaMessage (
        String role,
        String content
){}
