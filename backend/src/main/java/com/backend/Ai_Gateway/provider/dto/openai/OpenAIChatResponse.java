package com.backend.Ai_Gateway.provider.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenAIChatResponse(
        List<OpenAIChoice> choices,
        OpenAIUsage usage
) {
    public record OpenAIChoice(OpenAIMessage message) {}

    public record OpenAIUsage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens
    ) {}
}
