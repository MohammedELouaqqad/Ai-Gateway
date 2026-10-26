package com.backend.Ai_Gateway.provider.dto.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Fragment NDJSON renvoyé par Ollama en mode {@code stream=true}.
 * Distinct de {@link OllamaChatResponse} (réponse complète non-stream).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaStreamChunkDto(
        String model,
        OllamaStreamMessage message,
        boolean done,
        @JsonProperty("done_reason") String doneReason,
        @JsonProperty("prompt_eval_count") Integer promptEvalCount,
        @JsonProperty("eval_count") Integer evalCount
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OllamaStreamMessage(
            String role,
            String content
    ) {}
}
