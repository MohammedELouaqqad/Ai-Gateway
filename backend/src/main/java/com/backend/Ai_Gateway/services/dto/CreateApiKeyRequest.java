package com.backend.Ai_Gateway.services.dto;

import com.backend.Ai_Gateway.config.ContentLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * {@code expiresInDays} is optional (null = never expires, matching prior behavior) — the
 * caller opts into a TTL rather than one being forced on them. Null is valid for
 * {@code @Positive}/{@code @Max} (Bean Validation only checks a constraint when the value is
 * present), so no separate null-handling is needed here.
 */
public record CreateApiKeyRequest(
        @NotBlank @Size(max = ContentLimits.API_KEY_NAME_MAX) String name,
        @Positive @Max(3650) Integer expiresInDays
) {}
