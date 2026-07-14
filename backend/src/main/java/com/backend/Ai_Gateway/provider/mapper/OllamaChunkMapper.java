package com.backend.Ai_Gateway.provider.mapper;

import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ollama.OllamaStreamChunkDto;

/**
 * Traduit un fragment NDJSON Ollama vers le format pivot {@link ChatChunk}.
 */
public final class OllamaChunkMapper {

    private OllamaChunkMapper() {}

    public static ChatChunk toChatChunk(OllamaStreamChunkDto dto) {
        if (dto == null) {
            return ChatChunk.delta("");
        }

        if (dto.done()) {
            String finishReason = dto.doneReason() != null && !dto.doneReason().isBlank()
                    ? dto.doneReason()
                    : "stop";
            return ChatChunk.completed(
                    finishReason,
                    ChatChunk.TokenUsage.of(dto.promptEvalCount(), dto.evalCount())
            );
        }

        String content = dto.message() != null ? dto.message().content() : "";
        return ChatChunk.delta(content);
    }
}
