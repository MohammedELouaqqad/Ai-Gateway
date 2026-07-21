package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.config.GuardrailsProperties;
import com.backend.Ai_Gateway.services.UsageGuardService;
import com.backend.Ai_Gateway.services.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/guardrails")
@PreAuthorize("hasRole('ADMIN')")
public class GuardrailsController {

    private final UsageGuardService usageGuardService;
    private final UserService userService;

    public GuardrailsController(UsageGuardService usageGuardService, UserService userService) {
        this.usageGuardService = usageGuardService;
        this.userService = userService;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        GuardrailsProperties cfg = usageGuardService.effectiveConfig();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", cfg.isEnabled());
        body.put("requestsPerMinute", cfg.getRateLimit().getRequestsPerMinute());
        body.put("requestsPerDay", cfg.getRateLimit().getRequestsPerDay());
        body.put("globalBudgetPerDayEur", cfg.getBudget().getGlobalPerDayEur());
        body.put("userBudgetPerDayEur", cfg.getBudget().getUserPerDayEur());
        body.put("assumedCompletionTokens", cfg.getEstimate().getAssumedCompletionTokens());
        body.put("pricing", cfg.getPricing());
        return body;
    }

    @PutMapping("/config")
    public Map<String, Object> updateConfig(@RequestBody GuardrailsConfigUpdateRequest request) {
        usageGuardService.updateRuntimeConfig(
                request.enabled(),
                request.requestsPerMinute(),
                request.requestsPerDay(),
                request.globalBudgetPerDayEur(),
                request.userBudgetPerDayEur()
        );
        return config();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Long userId = userService.requireCurrentUserId();
        return usageGuardService.statusForUser(userId);
    }

    public record GuardrailsConfigUpdateRequest(
            Boolean enabled,
            Integer requestsPerMinute,
            Integer requestsPerDay,
            Double globalBudgetPerDayEur,
            Double userBudgetPerDayEur
    ) {}
}
