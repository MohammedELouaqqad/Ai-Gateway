package com.backend.Ai_Gateway.services.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    @Test
    void chunk_emptyOrBlankReturnsEmptyList() {
        assertThat(TextChunker.chunk(null, 100, 10)).isEmpty();
        assertThat(TextChunker.chunk("", 100, 10)).isEmpty();
        assertThat(TextChunker.chunk("   ", 100, 10)).isEmpty();
    }

    @Test
    void chunk_shortTextFitsInOneChunk() {
        List<String> chunks = TextChunker.chunk("hello world", 100, 10);
        assertThat(chunks).containsExactly("hello world");
    }

    @Test
    void chunk_splitsLongTextWithOverlap() {
        String text = "a".repeat(250);
        List<String> chunks = TextChunker.chunk(text, 100, 20);

        assertThat(chunks).hasSizeGreaterThan(1);
        // Every chunk except possibly the last respects the requested size.
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertThat(chunks.get(i).length()).isLessThanOrEqualTo(100);
        }
        // Concatenating without overlap awareness still covers the whole source length-wise.
        int totalCoveredAtLeast = chunks.stream().mapToInt(String::length).sum();
        assertThat(totalCoveredAtLeast).isGreaterThanOrEqualTo(text.length());
    }

    @Test
    void chunk_overlapGreaterOrEqualToSizeDoesNotHang() {
        // Regression guard: overlap >= chunkChars must never infinite-loop.
        List<String> chunks = TextChunker.chunk("x".repeat(50), 10, 10);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isLessThan(1000); // sanity bound, not just "didn't hang"
    }

    @Test
    void chunk_negativeOverlapTreatedAsZero() {
        List<String> chunks = TextChunker.chunk("x".repeat(30), 10, -5);
        assertThat(chunks).hasSize(3);
    }
}
