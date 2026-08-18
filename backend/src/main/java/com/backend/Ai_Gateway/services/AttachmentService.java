package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.UploadProperties;
import com.backend.Ai_Gateway.entity.Attachment;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.repositories.AttachmentRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.AttachmentDownload;
import com.backend.Ai_Gateway.services.dto.AttachmentView;
import com.backend.Ai_Gateway.services.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Self-service file upload — a user manages only their own files. Phase 1: image attachments,
 * NOT YET wired into the chat pipeline (see {@link Attachment} / {@link AttachmentRetentionJob}
 * javadoc). Every security control below applies regardless of that: an attacker doesn't care
 * whether the upload is consumed by a chat yet.
 *
 * <p>Content type is always the SNIFFED (magic-byte) type, never the client-sent header or the
 * filename extension — both are attacker-controlled and routinely wrong/forged.
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final UploadProperties properties;

    /**
     * In-memory sliding-window upload counter, independent of the LLM guardrails
     * (RPM/RPD/budget live in {@code services.guardrails} and are about LLM spend, not disk
     * usage). Same tradeoff the LLM guardrails made before Redis was added: fine for a single
     * instance, resets on restart, not shared across replicas. Move behind a
     * {@code RateLimitStore}-style abstraction if upload volume ever needs cross-replica limits.
     */
    private final Map<Long, Deque<Instant>> uploadTimestampsByUser = new ConcurrentHashMap<>();

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            UserRepository userRepository,
            FileStorageService fileStorageService,
            UploadProperties properties
    ) {
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.properties = properties;
    }

    @Transactional
    public AttachmentView upload(Long userId, MultipartFile file) {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("file: uploads are disabled");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RessourceNotFoundException("User not found"));

        enforceUploadRateLimit(userId);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file: must not be empty");
        }
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new IllegalArgumentException(
                    "file: exceeds max size of " + properties.getMaxSizeBytes() + " bytes");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("file: could not be read");
        }
        // Re-check after buffering: getSize() above trusts the multipart part's declared
        // length, this is the actual byte count.
        if (bytes.length > properties.getMaxSizeBytes()) {
            throw new IllegalArgumentException(
                    "file: exceeds max size of " + properties.getMaxSizeBytes() + " bytes");
        }

        String sniffedType = sniffContentType(bytes);
        if (sniffedType == null || !properties.getAllowedContentTypes().contains(sniffedType)) {
            throw new IllegalArgumentException(
                    "file: unsupported or unrecognized type — allowed: "
                            + String.join(", ", properties.getAllowedContentTypes()));
        }

        Attachment attachment = new Attachment();
        attachment.setOwner(user);
        attachment.setStorageKey(UUID.randomUUID().toString());
        attachment.setOriginalFilename(sanitizeFilename(file.getOriginalFilename()));
        attachment.setContentType(sniffedType);
        attachment.setSizeBytes(bytes.length);
        attachment.setSha256(sha256Hex(bytes));
        attachment.setCreatedAt(Instant.now());

        // DB row first (id/storageKey committed), then bytes — if the disk write below fails,
        // the compensating delete leaves no dangling DB row. The reverse order (bytes first)
        // can leak an on-disk file with no DB row on a late DB failure, which the orphan sweep
        // (DB-query-driven) could never find.
        Attachment saved = attachmentRepository.save(attachment);
        try {
            fileStorageService.store(saved.getStorageKey(), new ByteArrayInputStream(bytes), bytes.length);
        } catch (IOException e) {
            attachmentRepository.delete(saved);
            throw new IllegalStateException("Failed to persist upload", e);
        }

        return AttachmentView.from(saved);
    }

    @Transactional(readOnly = true)
    public AttachmentDownload download(Long userId, Long attachmentId) {
        Attachment attachment = requireOwned(userId, attachmentId);
        InputStream data;
        try {
            data = fileStorageService.read(attachment.getStorageKey());
        } catch (IOException e) {
            log.warn("Attachment {} has a DB row but no readable file on disk", attachmentId);
            throw new RessourceNotFoundException("Attachment not found");
        }
        return new AttachmentDownload(attachment.getContentType(), attachment.getOriginalFilename(), data);
    }

    /**
     * Owner-checked lookup, shared by {@link #download} and the chat-attachment path
     * ({@code ChatService#prepareChat}). 404, not 403, on mismatch — a caller must not be able
     * to distinguish "doesn't exist" from "belongs to someone else" (same IDOR-avoidance
     * pattern as {@code ConversationService}).
     */
    @Transactional(readOnly = true)
    public Attachment requireOwned(Long userId, Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RessourceNotFoundException("Attachment not found"));
        if (attachment.getOwner() == null || !attachment.getOwner().getId().equals(userId)) {
            throw new RessourceNotFoundException("Attachment not found");
        }
        return attachment;
    }

    /** Raw bytes for embedding into a provider request (base64) — see {@code ChatService}. */
    public byte[] readBytes(Attachment attachment) {
        try (InputStream in = fileStorageService.read(attachment.getStorageKey())) {
            return in.readAllBytes();
        } catch (IOException e) {
            log.warn("Attachment {} has a DB row but no readable file on disk", attachment.getId());
            throw new RessourceNotFoundException("Attachment not found");
        }
    }

    /**
     * Marks an attachment as referenced by a sent message — from this point on it's no longer
     * "orphaned" and {@link AttachmentRetentionJob} stops sweeping it. Idempotent (only the
     * first call sets the timestamp) since a regenerate could in principle re-touch the same
     * attachment.
     */
    @Transactional
    public void markAttached(Attachment attachment) {
        if (attachment.getAttachedAt() == null) {
            attachment.setAttachedAt(Instant.now());
            attachmentRepository.save(attachment);
        }
    }

    /** Called by {@link AttachmentRetentionJob}. @return number of orphaned attachments purged. */
    @Transactional
    public int purgeOrphaned(int ttlHours) {
        Instant cutoff = Instant.now().minus(ttlHours, ChronoUnit.HOURS);
        List<Attachment> orphaned = attachmentRepository.findByAttachedAtIsNullAndCreatedAtBefore(cutoff);
        for (Attachment a : orphaned) {
            fileStorageService.delete(a.getStorageKey());
            attachmentRepository.delete(a);
        }
        return orphaned.size();
    }

    private void enforceUploadRateLimit(Long userId) {
        int limit = properties.getRateLimit().getUploadsPerHour();
        if (limit <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        Deque<Instant> timestamps = uploadTimestampsByUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                throw new GuardrailsExceededException(
                        GuardrailsExceededException.Reason.UPLOAD_RATE_LIMIT,
                        "Limite d'upload : max " + limit + " fichiers / heure.",
                        Instant.now().plusSeconds(3600)
                );
            }
            timestamps.addLast(Instant.now());
        }
    }

    /**
     * Manual magic-byte sniff for the 3 whitelisted image types — no Apache Tika dependency for
     * Phase 1 (images only). Revisit with Tika if/when document types (PDF, RAG use case) land,
     * since manual signatures don't scale to arbitrary formats.
     */
    static String sniffContentType(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "image/png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "upload";
        }
        String base = name.replaceAll("[\\\\/]", "_").trim();
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
