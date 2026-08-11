package com.backend.Ai_Gateway.services.dto;

import com.backend.Ai_Gateway.entity.AuditLog;
import com.backend.Ai_Gateway.enums.AuditAction;

import java.time.Instant;
import java.util.Map;

public record AuditLogEntryView(
        Long id,
        Long actorUserId,
        String actorLabel,
        AuditAction action,
        String targetType,
        Long targetId,
        String targetLabel,
        Map<String, Object> details,
        Instant createdAt
) {
    public static AuditLogEntryView from(AuditLog log) {
        return new AuditLogEntryView(
                log.getId(),
                log.getActorUserId(),
                log.getActorLabel(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getTargetLabel(),
                log.getDetails(),
                log.getCreatedAt()
        );
    }
}
