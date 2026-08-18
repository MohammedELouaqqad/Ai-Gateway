package com.backend.Ai_Gateway.services.dto;

import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.enums.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code apiKey} is optional — Ollama needs none, vLLM/SGLang treat it as optional too. */
public record CreateProviderRequest(
        @NotBlank String name,
        @NotNull ProviderType type,
        String baseUrl,
        ProviderLocation location,
        Integer priority,
        boolean enabled,
        String apiKey
) {}
