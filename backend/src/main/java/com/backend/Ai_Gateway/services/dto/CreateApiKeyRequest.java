package com.backend.Ai_Gateway.services.dto;

import com.backend.Ai_Gateway.config.ContentLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequest(
        @NotBlank @Size(max = ContentLimits.API_KEY_NAME_MAX) String name
) {}
