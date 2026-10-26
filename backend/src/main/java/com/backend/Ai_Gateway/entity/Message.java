package com.backend.Ai_Gateway.entity;


import com.backend.Ai_Gateway.enums.MessageRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private MessageRole role;

    @Column(columnDefinition = "TEXT")
    private String content;
    private Instant createdAt;

    /** Nom technique du modèle ayant généré la réponse (assistant uniquement). */
    @Column(length = 100)
    private String modelName;

    /** Libellé affiché du modèle (assistant uniquement). */
    @Column(length = 150)
    private String modelDisplayName;

    /** True when the user requested a regeneration of this assistant reply. */
    @Column(nullable = false)
    private boolean regenerated = false;

    /** Newer assistant message that replaced this one after regenerate (nullable). */
    private Long supersededByMessageId;

    @JsonIgnore
    @ManyToOne
    private Conversation conversation;

    /**
     * Image attached to this message (USER only, in practice) — nullable, at most one per
     * message for v1. {@code @JsonIgnore}: the raw entity is never serialized directly, see
     * {@code MessageView#attachmentId} for the public-facing reference (frontend fetches the
     * actual bytes via {@code GET /api/files/{id}}).
     */
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "attachment_id")
    private Attachment attachment;

    /**
     * Number of RAG chunks actually retrieved and injected for this turn (assistant messages
     * only) — 0 means no RAG context was used, whether because no documents were attached,
     * none were READY, or retrieval failed and degraded gracefully (see
     * {@code RagRetrievalService}). This is the honest signal a "RAG used" badge needs: static
     * "N documents attached" alone can't tell the user whether retrieval actually succeeded
     * for THIS specific reply.
     * <p>
     * {@code columnDefinition} explicit default is required, not cosmetic — same lesson as
     * {@code Model.visionCapable}: a bare {@code @Column(nullable=false)} on a NEW field maps
     * to a plain {@code ADD COLUMN ... NOT NULL} with no default, which Postgres rejects on
     * this table (already has rows from real usage) with "column contains null values".
     */
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    private int ragChunksUsed = 0;
}
