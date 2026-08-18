package com.backend.Ai_Gateway.services.dto;

import com.backend.Ai_Gateway.entity.RagDocument;
import com.backend.Ai_Gateway.enums.RagDocumentStatus;

import java.time.Instant;

/** Public view of a RAG document — never exposes {@code storageKey}/{@code sha256}. */
public record RagDocumentView(
        Long id,
        String filename,
        String contentType,
        long sizeBytes,
        RagDocumentStatus status,
        int chunkCount,
        String errorMessage,
        Instant createdAt,
        Instant processedAt
) {
    public static RagDocumentView from(RagDocument document) {
        return new RagDocumentView(
                document.getId(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStatus(),
                document.getChunkCount(),
                document.getErrorMessage(),
                document.getCreatedAt(),
                document.getProcessedAt()
        );
    }
}
