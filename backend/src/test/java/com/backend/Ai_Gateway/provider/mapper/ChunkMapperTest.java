package com.backend.Ai_Gateway.provider.mapper;

import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ollama.OllamaStreamChunkDto;
import com.backend.Ai_Gateway.provider.dto.openai.OpenAiStreamChunkDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkMapperTest {

    @Test
    void ollamaDelta_onlyContentFilled() {
        OllamaStreamChunkDto dto = new OllamaStreamChunkDto(
                "qwen",
                new OllamaStreamChunkDto.OllamaStreamMessage("assistant", "Hel"),
                false,
                null,
                null,
                null
        );

        ChatChunk chunk = OllamaChunkMapper.toChatChunk(dto);

        assertFalse(chunk.done());
        assertEquals("Hel", chunk.content());
        assertNull(chunk.finishReason());
        assertNull(chunk.usage());
    }

    @Test
    void ollamaDone_usageAndFinishReasonFilled() {
        OllamaStreamChunkDto dto = new OllamaStreamChunkDto(
                "qwen",
                new OllamaStreamChunkDto.OllamaStreamMessage("assistant", ""),
                true,
                "stop",
                12,
                4
        );

        ChatChunk chunk = OllamaChunkMapper.toChatChunk(dto);

        assertTrue(chunk.done());
        assertEquals("", chunk.content());
        assertEquals("stop", chunk.finishReason());
        assertEquals(12, chunk.usage().promptTokens());
        assertEquals(4, chunk.usage().completionTokens());
        assertEquals(16, chunk.usage().totalTokens());
    }

    @Test
    void openAiDelta_onlyContentFilled() {
        OpenAiStreamChunkDto dto = new OpenAiStreamChunkDto(
                "chatcmpl-1",
                List.of(new OpenAiStreamChunkDto.OpenAiStreamChoice(
                        0,
                        new OpenAiStreamChunkDto.OpenAiStreamDelta(null, "Bon"),
                        null
                )),
                null
        );

        ChatChunk chunk = OpenAiChunkMapper.toChatChunk(dto);

        assertFalse(chunk.done());
        assertEquals("Bon", chunk.content());
        assertNull(chunk.finishReason());
        assertNull(chunk.usage());
    }

    @Test
    void openAiDone_withUsage() {
        OpenAiStreamChunkDto dto = new OpenAiStreamChunkDto(
                "chatcmpl-1",
                List.of(new OpenAiStreamChunkDto.OpenAiStreamChoice(0, new OpenAiStreamChunkDto.OpenAiStreamDelta(null, null), "stop")),
                new OpenAiStreamChunkDto.OpenAiStreamUsage(10, 3, 13)
        );

        ChatChunk chunk = OpenAiChunkMapper.toChatChunk(dto);

        assertTrue(chunk.done());
        assertEquals("", chunk.content());
        assertEquals("stop", chunk.finishReason());
        assertEquals(10, chunk.usage().promptTokens());
        assertEquals(3, chunk.usage().completionTokens());
        assertEquals(13, chunk.usage().totalTokens());
    }
}
