package com.backend.Ai_Gateway.services.guardrails;

import com.backend.Ai_Gateway.config.RedisGuardProperties;
import com.backend.Ai_Gateway.services.UsageGuardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BudgetReconciliationJobTest {

    @Mock UsageGuardService usageGuardService;

    @Test
    void reconcile_delegatesToUsageGuard() {
        BudgetReconciliationJob job = new BudgetReconciliationJob(usageGuardService, new RedisGuardProperties());
        job.reconcile();
        verify(usageGuardService).reconcileGlobalBudgetFromSql();
    }

    @Test
    void reconcile_swallowsFailures() {
        doThrow(new RuntimeException("redis down")).when(usageGuardService).reconcileGlobalBudgetFromSql();
        BudgetReconciliationJob job = new BudgetReconciliationJob(usageGuardService, new RedisGuardProperties());
        job.reconcile(); // must not throw
        verify(usageGuardService).reconcileGlobalBudgetFromSql();
    }
}
