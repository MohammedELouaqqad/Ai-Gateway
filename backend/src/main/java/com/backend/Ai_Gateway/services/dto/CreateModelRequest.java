package com.backend.Ai_Gateway.services.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateModelRequest(
        @NotBlank String displayName,
        @NotBlank String name,
        @NotNull Long providerId,
        boolean enabled,
        boolean isDefault
) {}
