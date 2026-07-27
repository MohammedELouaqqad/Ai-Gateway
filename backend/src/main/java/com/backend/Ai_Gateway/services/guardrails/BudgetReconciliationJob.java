package com.backend.Ai_Gateway.services.guardrails;

import com.backend.Ai_Gateway.config.RedisGuardProperties;
import com.backend.Ai_Gateway.services.UsageGuardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Réconciliation budget Redis ← SUM Postgres (récupère réservations orphelines après crash pod).
 */
@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class BudgetReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(BudgetReconciliationJob.class);

    private final UsageGuardService usageGuardService;
    private final RedisGuardProperties redisProps;

    public BudgetReconciliationJob(UsageGuardService usageGuardService, RedisGuardProperties redisProps) {
        this.usageGuardService = usageGuardService;
        this.redisProps = redisProps;
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
    public void reconcile() {
        try {
            usageGuardService.reconcileGlobalBudgetFromSql();
            log.debug("Budget global reconciled from SQL (interval≈{}m)",
                    redisProps.getBudget().getReconcileIntervalMinutes());
        } catch (Exception e) {
            log.warn("Budget reconciliation failed: {}", e.toString());
        }
    }
}
