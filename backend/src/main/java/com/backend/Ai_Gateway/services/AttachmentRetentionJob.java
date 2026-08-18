package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.UploadProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purges uploaded attachments older than {@code app.uploads.orphan-ttl-hours} that were never
 * attached to a sent message. Until the vision-chat wiring lands (Phase 2), NOTHING sets
 * {@code Attachment.attachedAt} — every upload is an orphan by definition and gets swept after
 * the TTL. That's intentional for this phase: it caps disk usage instead of accumulating
 * uploads with no consumer forever. Runs hourly since the TTL is measured in hours (unlike
 * {@link ConversationRetentionJob}, which is daily for a days-scale TTL).
 */
@Component
public class AttachmentRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AttachmentRetentionJob.class);
    private static final long ONE_HOUR_MS = 60 * 60 * 1000L;

    private final AttachmentService attachmentService;
    private final UploadProperties uploadProperties;

    public AttachmentRetentionJob(AttachmentService attachmentService, UploadProperties uploadProperties) {
        this.attachmentService = attachmentService;
        this.uploadProperties = uploadProperties;
    }

    @Scheduled(fixedDelay = ONE_HOUR_MS, initialDelay = 120_000L)
    public void purgeOrphanedUploads() {
        try {
            int purged = attachmentService.purgeOrphaned(uploadProperties.getOrphanTtlHours());
            if (purged > 0) {
                log.info("Uploads: purged {} orphaned attachment(s) older than {}h",
                        purged, uploadProperties.getOrphanTtlHours());
            }
        } catch (Exception e) {
            log.warn("Orphaned upload purge failed: {}", e.toString());
        }
    }
}
