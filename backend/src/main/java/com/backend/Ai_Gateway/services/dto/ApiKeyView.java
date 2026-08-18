package com.backend.Ai_Gateway.services.dto;

import com.backend.Ai_Gateway.entity.ApiKey;

import java.time.Instant;

/** Public view of an API key — never exposes {@code keyHash}. */
public record ApiKeyView(
        Long id,
        String name,
        String keyPrefix,
        boolean enabled,
        Instant createdAt,
        Instant expiresAt
) {
    public static ApiKeyView from(ApiKey apiKey) {
        return new ApiKeyView(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                apiKey.isEnabled(),
                apiKey.getCreatedAt(),
                apiKey.getExpiresAt()
        );
    }
}
