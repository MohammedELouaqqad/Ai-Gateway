package com.backend.Ai_Gateway.provider.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Body OpenAI dédié au streaming ({@code stream=true}), distinct de {@link OpenAIChatRequest}.
 */
public record OpenAIStreamChatRequest(
        String model,
        List<OpenAIMessage> messages,
        boolean stream,
        @JsonProperty("stream_options") StreamOptions streamOptions
) {
    public record StreamOptions(
            @JsonProperty("include_usage") boolean includeUsage
    ) {}

    public static OpenAIStreamChatRequest of(String model, List<OpenAIMessage> messages) {
        return new OpenAIStreamChatRequest(model, messages, true, new StreamOptions(true));
    }
}
