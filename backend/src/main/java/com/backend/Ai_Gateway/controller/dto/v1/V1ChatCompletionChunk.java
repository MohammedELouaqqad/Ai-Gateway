package com.backend.Ai_Gateway.controller.dto.v1;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** OpenAI /v1/chat/completions streaming chunk ({@code chat.completion.chunk}). */
public record V1ChatCompletionChunk(
        String id,
        String object,
        long created,
        String model,
        List<Choice> choices
) {
    public record Choice(
            int index,
            Delta delta,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    public record Delta(String role, String content) {}

    /** First frame — carries the OpenAI-conventional {@code role: "assistant"} opener. */
    public static V1ChatCompletionChunk opening(String id, long created, String modelName) {
        return new V1ChatCompletionChunk(
                id, "chat.completion.chunk", created, modelName,
                List.of(new Choice(0, new Delta("assistant", ""), null))
        );
    }

    public static V1ChatCompletionChunk delta(String id, long created, String modelName, String content) {
        return new V1ChatCompletionChunk(
                id, "chat.completion.chunk", created, modelName,
                List.of(new Choice(0, new Delta(null, content), null))
        );
    }

    public static V1ChatCompletionChunk done(String id, long created, String modelName) {
        return new V1ChatCompletionChunk(
                id, "chat.completion.chunk", created, modelName,
                List.of(new Choice(0, new Delta(null, null), "stop"))
        );
    }
}
