package com.backend.Ai_Gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sync-chat retry policy for {@link com.backend.Ai_Gateway.services.ChatExecutor} — retry
 * the SAME provider on a transient failure. Split out of the old RoutingProperties (which
 * bundled it with automatic model-choice config, a different, now-removed concern).
 */
@ConfigurationProperties(prefix = "app.retry")
public class RetryProperties {

    private int maxRetries = 1;
    private long retryBackoffMs = 200;

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, maxRetries);
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = Math.max(0, retryBackoffMs);
    }
}
