package com.backend.Ai_Gateway.entity;

import com.backend.Ai_Gateway.enums.AuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * One successful admin mutation — append-only, no update/delete exposed anywhere in the app.
 * {@code actorLabel}/{@code targetLabel} are snapshots taken at write time (not live joins):
 * a renamed/deleted user or provider must never make the history unreadable. {@code details}
 * is a structured diff (old/new per changed field) — see {@code services/AuditDiffs} — and must
 * never contain a secret value (provider API key, etc.); callers redact those before this is
 * ever built.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Local user id of the acting admin, resolved from the JWT at the time of the action. */
    private Long actorUserId;

    /** Snapshot of the acting admin's username/email at the time of the action. */
    private String actorLabel;

    @Enumerated(EnumType.STRING)
    private AuditAction action;

    /** e.g. "PROVIDER", "MODEL", "USER", "GUARDRAILS". */
    private String targetType;

    /** Null when there's no single identifiable target (e.g. global guardrails config). */
    private Long targetId;

    /** Snapshot of the target's display name at the time of the action; nullable. */
    private String targetLabel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    private Instant createdAt;
}
