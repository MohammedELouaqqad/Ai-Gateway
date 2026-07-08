package com.backend.Ai_Gateway.services.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProviderRequest(
        @NotBlank String name,
        String baseUrl,
        Integer priority
) {}
