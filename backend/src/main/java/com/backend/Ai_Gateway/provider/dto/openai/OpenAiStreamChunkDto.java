package com.backend.Ai_Gateway.provider.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Fragment SSE {@code data:} renvoyé par OpenAI en mode stream.
 * Distinct de {@link OpenAIChatResponse} (réponse complète non-stream).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiStreamChunkDto(
        String id,
        List<OpenAiStreamChoice> choices,
        OpenAiStreamUsage usage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpenAiStreamChoice(
            Integer index,
            OpenAiStreamDelta delta,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpenAiStreamDelta(
            String role,
            String content
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpenAiStreamUsage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {}
}
