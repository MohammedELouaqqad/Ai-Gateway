package com.backend.Ai_Gateway.controller.dto.v1;

import com.backend.Ai_Gateway.config.ContentLimits;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** OpenAI /v1/chat/completions request — only the fields this gateway acts on; rest ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record V1ChatCompletionRequest(
        @NotBlank String model,
        @NotEmpty @Size(max = ContentLimits.V1_MESSAGES_MAX_COUNT) @Valid java.util.List<V1ChatMessage> messages,
        Boolean stream
) {
    public boolean isStreaming() {
        return Boolean.TRUE.equals(stream);
    }
}
