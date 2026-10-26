package com.backend.Ai_Gateway.services.dto;

import com.backend.Ai_Gateway.entity.Attachment;

import java.time.Instant;

/** Public view of an uploaded file — never exposes {@code storageKey} (internal disk path). */
public record AttachmentView(
        Long id,
        String filename,
        String contentType,
        long sizeBytes,
        Instant createdAt
) {
    public static AttachmentView from(Attachment attachment) {
        return new AttachmentView(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getCreatedAt()
        );
    }
}
