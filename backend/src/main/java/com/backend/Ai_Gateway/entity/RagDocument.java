package com.backend.Ai_Gateway.entity;


import com.backend.Ai_Gateway.enums.RagDocumentStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * An uploaded document (PDF, v1) queued for RAG ingestion. Chunk text/vectors live in Qdrant,
 * NOT here — this row is metadata + lifecycle status only (see {@link RagDocumentStatus}).
 * Storage (disk bytes) reuses the same {@code FileStorageService} as chat image attachments —
 * generic by design, see {@link Attachment} javadoc.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Table(indexes = {
        @Index(name = "idx_rag_document_owner", columnList = "owner_id"),
        @Index(name = "idx_rag_document_status", columnList = "status")
})
public class RagDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** Opaque, server-generated (UUID) key on disk — see {@code LocalFileStorageService}. */
    @JsonIgnore
    @Column(nullable = false, unique = true, length = 64)
    private String storageKey;

    @Column(length = 255)
    private String originalFilename;

    /** Always {@code application/pdf} for v1 — kept as a column (not hardcoded) so a future
     *  format doesn't need a schema change. */
    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @JsonIgnore
    @Column(length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RagDocumentStatus status;

    /** Set once ingestion succeeds — 0 until then. */
    private int chunkCount;

    /** Set only on {@link RagDocumentStatus#FAILED} — truncated exception message, never a full
     *  stack trace (avoids leaking internal paths/library versions to the owning user). */
    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant processedAt;
}
