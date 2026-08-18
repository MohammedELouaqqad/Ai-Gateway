package com.backend.Ai_Gateway.provider;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamLineBufferTest {

    @Test
    void reassemblesJsonSplitAcrossChunks() {
        StreamLineBuffer buffer = new StreamLineBuffer();
        List<String> lines = new ArrayList<>();
        lines.addAll(buffer.appendAndDrain("data: {\"id\":\"chatcmpl-E1XZg"));
        lines.addAll(buffer.appendAndDrain("ABC\",\"object\":\"chat.completion.chunk\"}\n"));
        lines.addAll(buffer.appendAndDrain("data: [DONE]\n"));

        assertEquals(2, lines.size());
        assertEquals("data: {\"id\":\"chatcmpl-E1XZgABC\",\"object\":\"chat.completion.chunk\"}", lines.get(0));
        assertEquals("data: [DONE]", lines.get(1));
    }

    @Test
    void handlesCrlfAndFlush() {
        StreamLineBuffer buffer = new StreamLineBuffer();
        assertEquals(List.of("hello"), buffer.appendAndDrain("hello\r\n"));
        assertEquals(List.of(), buffer.appendAndDrain("partial"));
        assertEquals(List.of("partial"), buffer.flush());
    }
}
