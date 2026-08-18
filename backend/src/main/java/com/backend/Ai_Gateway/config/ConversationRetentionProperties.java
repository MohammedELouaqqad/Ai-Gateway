package com.backend.Ai_Gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Grace period before a deleted conversation is physically removed. Not a user-facing
 * trash/undo feature — the row is kept internally as a safety margin only, then permanently
 * purged by {@link com.backend.Ai_Gateway.services.ConversationRetentionJob} once older than
 * {@link #retentionDays}.
 */
@ConfigurationProperties(prefix = "app.conversation-retention")
public class ConversationRetentionProperties {

    private int retentionDays = 30;

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = Math.max(1, retentionDays);
    }
}
