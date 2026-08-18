package com.backend.Ai_Gateway.provider.dto;

/**
 * An image attached to a {@link ChatMessage}, ready to embed in a provider request —
 * {@code base64Data} is the raw file bytes, already base64-encoded, no data URI prefix (each
 * provider family builds its own wire format from this, see {@code OpenAiCompatibleProvider}
 * vs {@code OllamaProvider}).
 */
public record ChatAttachment(
        String contentType,
        String base64Data
) {}
