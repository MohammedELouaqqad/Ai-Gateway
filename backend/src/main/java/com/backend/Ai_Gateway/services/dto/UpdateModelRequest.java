package com.backend.Ai_Gateway.services.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code enabled}/{@code isDefault} deliberately absent here — {@code PATCH /{id}/status} and
 * {@code PATCH /{id}/default} own those, each with its own business rule (can't enable if the
 * provider is disabled, {@code ensureModelUsable} before becoming default) and its own audit
 * action ({@code MODEL_ENABLE}/{@code MODEL_SET_DEFAULT}). Folding them into this generic
 * update would bypass both.
 */
public record UpdateModelRequest(
        @NotBlank String displayName,
        @NotBlank String name,
        @NotNull Long providerId,
        boolean visionCapable
) {}
