package com.backend.Ai_Gateway.provider.dto.openai;

/** One element of an {@link OpenAIOutgoingMessage} multimodal {@code content} array. */
public record OpenAITextPart(String type, String text) {
    public static OpenAITextPart of(String text) {
        return new OpenAITextPart("text", text);
    }
}
