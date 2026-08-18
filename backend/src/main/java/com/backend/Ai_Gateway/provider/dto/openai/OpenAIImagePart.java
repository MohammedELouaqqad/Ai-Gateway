package com.backend.Ai_Gateway.provider.dto.openai;

/**
 * One element of an {@link OpenAIOutgoingMessage} multimodal {@code content} array — the
 * OpenAI vision format: a data URI, not a bare base64 string (unlike Ollama's {@code images}).
 */
public record OpenAIImagePart(String type, ImageUrl image_url) {
    public record ImageUrl(String url) {}

    public static OpenAIImagePart of(String contentType, String base64Data) {
        return new OpenAIImagePart("image_url", new ImageUrl("data:" + contentType + ";base64," + base64Data));
    }
}
