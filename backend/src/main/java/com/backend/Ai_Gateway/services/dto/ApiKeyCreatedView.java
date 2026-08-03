package com.backend.Ai_Gateway.services.dto;

import com.backend.Ai_Gateway.entity.ApiKey;

import java.time.Instant;

/**
 * Returned once, at creation time only. {@code rawKey} is never persisted or
 * retrievable again — only {@link ApiKey#getKeyHash()} is stored.
 */
public record ApiKeyCreatedView(
        Long id,
        String name,
        String keyPrefix,
        String rawKey,
        boolean enabled,
        Instant createdAt
) {
    public static ApiKeyCreatedView from(ApiKey apiKey, String rawKey) {
        return new ApiKeyCreatedView(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                rawKey,
                apiKey.isEnabled(),
                apiKey.getCreatedAt()
        );
    }
}
