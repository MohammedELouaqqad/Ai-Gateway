package com.backend.Ai_Gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * File upload limits — Phase 1 (image attachments only: {@code POST /api/files}). Not yet
 * wired into the chat pipeline (see {@code AttachmentRetentionJob} javadoc for why every
 * upload is currently treated as an orphan). Config over hardcoding, same pattern as
 * {@link GuardrailsProperties} / {@link ConversationRetentionProperties}.
 */
@ConfigurationProperties(prefix = "app.uploads")
public class UploadProperties {

    private boolean enabled = true;

    /** Hard cap on a single file, checked after multipart parsing (see application.properties
     *  for the Spring-level {@code spring.servlet.multipart.max-file-size} pre-cap). */
    private long maxSizeBytes = 10_000_000L;

    /** Whitelist, matched against the SNIFFED (magic-byte) content type — never the client-sent
     *  {@code Content-Type} header or file extension, both of which are attacker-controlled. */
    private List<String> allowedContentTypes = List.of("image/png", "image/jpeg", "image/webp");

    /** Where files physically live (local disk — see {@code LocalFileStorageService}). */
    private String storagePath = "./data/uploads";

    /** Uploads never attached to a sent message get purged after this many hours. */
    private int orphanTtlHours = 24;

    private RateLimit rateLimit = new RateLimit();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes != null ? allowedContentTypes : List.of();
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public int getOrphanTtlHours() {
        return orphanTtlHours;
    }

    public void setOrphanTtlHours(int orphanTtlHours) {
        this.orphanTtlHours = Math.max(1, orphanTtlHours);
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class RateLimit {
        /** Independent of the LLM guardrails RPM/RPD — this bounds upload volume (disk), not
         *  LLM spend. 0 disables the check. */
        private int uploadsPerHour = 30;

        public int getUploadsPerHour() {
            return uploadsPerHour;
        }

        public void setUploadsPerHour(int uploadsPerHour) {
            this.uploadsPerHour = uploadsPerHour;
        }
    }
}
