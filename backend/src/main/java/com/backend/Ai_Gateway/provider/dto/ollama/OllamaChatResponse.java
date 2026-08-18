package com.backend.Ai_Gateway.provider.dto.ollama;

public record OllamaChatResponse(
        String model,
        OllamaMessage message,     // ← objet imbriqué
        Integer prompt_eval_count,
        Integer eval_count
) {}