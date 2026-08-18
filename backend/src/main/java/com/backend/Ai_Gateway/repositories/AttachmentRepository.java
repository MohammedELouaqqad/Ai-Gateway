package com.backend.Ai_Gateway.repositories;

import com.backend.Ai_Gateway.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /** Used by {@code AttachmentRetentionJob} — never-attached uploads past the TTL. */
    List<Attachment> findByAttachedAtIsNullAndCreatedAtBefore(Instant cutoff);
}
