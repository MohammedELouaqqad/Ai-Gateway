package com.backend.Ai_Gateway.exceptions;

import java.time.Instant;

/**
 * Garde-fou rate limit / budget — HTTP 429.
 */
public class GuardrailsExceededException extends RuntimeException {

    public enum Reason {
        RATE_LIMIT_MINUTE("rate_limit_minute"),
        RATE_LIMIT_DAY("rate_limit_day"),
        BUDGET_USER("budget_user"),
        BUDGET_GLOBAL("budget_global"),
        /** Upload volume guard ({@code AttachmentService}) — independent of LLM RPM/RPD/budget
         *  above; bounds disk usage, not LLM spend. Reuses this exception purely for its 429
         *  response shape (reason/resetsAt), not the LLM guardrails pipeline itself. */
        UPLOAD_RATE_LIMIT("upload_rate_limit");

        private final String metricLabel;

        Reason(String metricLabel) {
            this.metricLabel = metricLabel;
        }

        public String metricLabel() {
            return metricLabel;
        }
    }

    private final Reason reason;
    private final Instant resetsAt;

    public GuardrailsExceededException(Reason reason, String message, Instant resetsAt) {
        super(message);
        this.reason = reason;
        this.resetsAt = resetsAt;
    }

    public Reason getReason() {
        return reason;
    }

    public Instant getResetsAt() {
        return resetsAt;
    }
}
