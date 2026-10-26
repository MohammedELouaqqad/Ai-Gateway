package com.backend.Ai_Gateway.provider.dto.openai;

/**
 * Request-only message shape — distinct from {@link OpenAIMessage} (response parsing, where
 * {@code content} is always a plain string). {@code content} here is deliberately {@link Object}:
 * a plain {@code String} for every text-only message (identical JSON to before this type
 * existed), or a {@code List<Object>} of {@link OpenAITextPart}/{@link OpenAIImagePart} when the
 * message carries an attachment — Jackson serializes either shape correctly from the runtime
 * type, no custom serializer needed (write-only, no deserialization of this type).
 */
public record OpenAIOutgoingMessage(
        String role,
        Object content
) {}
