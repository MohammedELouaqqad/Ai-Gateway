package com.backend.Ai_Gateway.services.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Character-based chunking (same ~4-chars/token approximation used elsewhere in this codebase,
 * e.g. {@code GuardrailsProperties.Estimate}) — no tokenizer dependency for v1. Overlap keeps a
 * fact split across a chunk boundary retrievable from either neighboring chunk.
 */
public final class TextChunker {

    private TextChunker() {}

    public static List<String> chunk(String text, int chunkChars, int overlapChars) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int size = Math.max(chunkChars, 1);
        int overlap = Math.max(0, Math.min(overlapChars, size - 1)); // never >= size (infinite loop guard)

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            if (end == text.length()) {
                break;
            }
            start = end - overlap;
        }
        return chunks;
    }
}
