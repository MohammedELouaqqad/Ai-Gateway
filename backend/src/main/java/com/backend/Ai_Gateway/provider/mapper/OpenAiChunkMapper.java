package com.backend.Ai_Gateway.provider.mapper;

import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAiStreamChunkDto;

/**
 * Traduit un fragment SSE OpenAI vers le format pivot {@link ChatChunk}.
 */
public final class OpenAiChunkMapper {

    private OpenAiChunkMapper() {}

    public static ChatChunk toChatChunk(OpenAiStreamChunkDto dto) {
        if (dto == null) {
            return ChatChunk.delta("");
        }

        OpenAiStreamChunkDto.OpenAiStreamChoice choice =
                (dto.choices() != null && !dto.choices().isEmpty()) ? dto.choices().get(0) : null;

        String finishReason = choice != null ? choice.finishReason() : null;
        String deltaContent = choice != null && choice.delta() != null ? choice.delta().content() : null;
        boolean hasUsage = dto.usage() != null;
        boolean done = finishReason != null || hasUsage;

        // OpenAI peut envoyer le dernier fragment de texte + finish_reason dans le même event
        if (done && deltaContent != null && !deltaContent.isEmpty() && !hasUsage) {
            return ChatChunk.delta(deltaContent);
        }

        if (done) {
            String reason = finishReason != null && !finishReason.isBlank() ? finishReason : "stop";
            ChatChunk.TokenUsage usage;
            if (dto.usage() != null) {
                Integer prompt = dto.usage().promptTokens();
                Integer completion = dto.usage().completionTokens();
                Integer total = dto.usage().totalTokens();
                if (total == null && (prompt != null || completion != null)) {
                    usage = ChatChunk.TokenUsage.of(prompt, completion);
                } else {
                    usage = new ChatChunk.TokenUsage(
                            prompt != null ? prompt : 0,
                            completion != null ? completion : 0,
                            total != null ? total : 0
                    );
                }
            } else {
                usage = ChatChunk.TokenUsage.of(0, 0);
            }
            return ChatChunk.completed(reason, usage);
        }

        return ChatChunk.delta(deltaContent == null ? "" : deltaContent);
    }
}
