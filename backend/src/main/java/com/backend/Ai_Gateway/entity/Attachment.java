package com.backend.Ai_Gateway.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * An uploaded file (Phase 1: images only). Kept as its own entity — not a column on
 * {@link Message} — precisely so that text-only chat stays byte-for-byte unchanged while this
 * lands, and so a future RAG/document use case can reuse the same storage/ownership plumbing.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Table(indexes = {
        @Index(name = "idx_attachment_owner", columnList = "owner_id"),
        @Index(name = "idx_attachment_attached_at", columnList = "attachedAt")
})
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** Opaque, server-generated (UUID) key on disk — NEVER derived from the client-supplied
     *  filename (path traversal). See {@code LocalFileStorageService}. Internal implementation
     *  detail, never serialized (this entity is now also embedded in {@code MessageView}-adjacent
     *  responses, not just behind {@code AttachmentView}). */
    @JsonIgnore
    @Column(nullable = false, unique = true, length = 64)
    private String storageKey;

    /** Original filename as supplied by the client — display only, never used to build a path. */
    @Column(length = 255)
    private String originalFilename;

    /** SNIFFED (magic-byte) content type — see {@code AttachmentService#sniffContentType}. */
    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @JsonIgnore
    @Column(length = 64)
    private String sha256;

    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Set once this attachment is actually referenced by a sent message. Nothing sets this yet
     * (chat wiring is Phase 2) — every row is currently "orphaned" by definition and purged by
     * {@code AttachmentRetentionJob} after {@code app.uploads.orphan-ttl-hours}. Intentional:
     * caps disk usage instead of accumulating unreferenced uploads indefinitely.
     */
    private Instant attachedAt;
}
