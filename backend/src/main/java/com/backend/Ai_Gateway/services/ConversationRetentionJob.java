package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.ConversationRetentionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Conversations soft-deleted (see {@link ConversationService#deleteConversation}) longer than
 * {@link ConversationRetentionProperties#getRetentionDays()} are purged for good — this is the
 * only place that actually happens, since there's no user-facing restore. Runs once a day —
 * retention is measured in days, no need for tighter polling.
 */
@Component
public class ConversationRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(ConversationRetentionJob.class);
    private static final long ONE_DAY_MS = 24 * 60 * 60 * 1000L;

    private final ConversationService conversationService;
    private final ConversationRetentionProperties retentionProperties;

    public ConversationRetentionJob(
            ConversationService conversationService,
            ConversationRetentionProperties retentionProperties
    ) {
        this.conversationService = conversationService;
        this.retentionProperties = retentionProperties;
    }

    @Scheduled(fixedDelay = ONE_DAY_MS, initialDelay = 60_000L)
    public void purgeExpiredTrash() {
        try {
            int purged = conversationService.purgeExpiredConversations(retentionProperties.getRetentionDays());
            if (purged > 0) {
                log.info("Conversation trash: purged {} conversation(s) older than {}d",
                        purged, retentionProperties.getRetentionDays());
            }
        } catch (Exception e) {
            log.warn("Conversation trash purge failed: {}", e.toString());
        }
    }
}
