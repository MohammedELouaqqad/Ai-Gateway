package com.backend.Ai_Gateway.provider.dto.ollama;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code images}: base64 strings, no data-URI prefix — Ollama's own wire format, distinct from
 * OpenAI's {@code content: [{type:"image_url", ...}]} array (see {@code OpenAiCompatibleProvider}).
 * {@code NON_EMPTY} so a text-only message serializes with no {@code images} field at all —
 * byte-for-byte the same request body as before this field existed.
 */
public record OllamaMessage (
        String role,
        String content,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> images
){
    public OllamaMessage(String role, String content) {
        this(role, content, List.of());
    }
}
