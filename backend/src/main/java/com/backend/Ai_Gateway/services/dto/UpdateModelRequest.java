package com.backend.Ai_Gateway.services.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateModelRequest(
        @NotBlank String displayName,
        @NotBlank String name
) {}
