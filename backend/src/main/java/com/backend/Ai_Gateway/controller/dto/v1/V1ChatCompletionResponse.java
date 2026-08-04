package com.backend.Ai_Gateway.controller.dto.v1;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** OpenAI /v1/chat/completions non-streaming response shape. */
public record V1ChatCompletionResponse(
        String id,
        String object,
        long created,
        String model,
        List<Choice> choices,
        Usage usage
) {
    public record Choice(
            int index,
            V1ChatMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {}

    public static V1ChatCompletionResponse of(String modelName, String content, int promptTokens, int completionTokens) {
        return new V1ChatCompletionResponse(
                "chatcmpl-" + java.util.UUID.randomUUID(),
                "chat.completion",
                System.currentTimeMillis() / 1000,
                modelName,
                List.of(new Choice(0, new V1ChatMessage("assistant", content), "stop")),
                new Usage(promptTokens, completionTokens, promptTokens + completionTokens)
        );
    }
}
