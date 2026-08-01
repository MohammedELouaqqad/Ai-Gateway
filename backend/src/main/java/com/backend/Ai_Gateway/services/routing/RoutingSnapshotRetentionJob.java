package com.backend.Ai_Gateway.services.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * GDPR / storage: purge prompt & response snapshots past retention.
 * Verdicts and cost metrics are kept; only user text is cleared.
 */
@Component
public class RoutingSnapshotRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RoutingSnapshotRetentionJob.class);

    private final RoutingService routingService;

    public RoutingSnapshotRetentionJob(RoutingService routingService) {
        this.routingService = routingService;
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 120_000L)
    public void purge() {
        try {
            int cleared = routingService.purgeExpiredSnapshots();
            if (cleared > 0) {
                log.info("Cleared routing decision snapshots on {} rows (retention policy)", cleared);
            }
        } catch (Exception e) {
            log.warn("Routing snapshot retention purge failed: {}", e.toString());
        }
    }
}
