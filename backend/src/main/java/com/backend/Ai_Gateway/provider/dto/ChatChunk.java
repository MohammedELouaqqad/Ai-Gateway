package com.backend.Ai_Gateway.provider.dto;

/**
 * Format pivot streaming renvoyé au client (SSE).
 * Règle : si {@code done=false}, seul {@code content} est renseigné ;
 * {@code finishReason} et {@code usage} restent null jusqu'au chunk final ({@code done=true}).
 */
public record ChatChunk(
        String content,
        boolean done,
        String finishReason,
        TokenUsage usage
) {
    public record TokenUsage(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
        public static TokenUsage of(Integer promptTokens, Integer completionTokens) {
            int prompt = promptTokens != null ? promptTokens : 0;
            int completion = completionTokens != null ? completionTokens : 0;
            return new TokenUsage(prompt, completion, prompt + completion);
        }
    }

    public static ChatChunk delta(String content) {
        return new ChatChunk(content == null ? "" : content, false, null, null);
    }

    public static ChatChunk completed(String finishReason, TokenUsage usage) {
        return new ChatChunk("", true, finishReason, usage);
    }
}
