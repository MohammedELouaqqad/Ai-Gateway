package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.config.RoutingProperties;
import com.backend.Ai_Gateway.services.UserService;
import com.backend.Ai_Gateway.services.routing.RoutingDecision;
import com.backend.Ai_Gateway.services.routing.RoutingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/routing")
@PreAuthorize("hasRole('ADMIN')")
public class RoutingController {

    private final RoutingService routingService;
    private final UserService userService;

    public RoutingController(RoutingService routingService, UserService userService) {
        this.routingService = routingService;
        this.userService = userService;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        RoutingProperties cfg = routingService.effectiveConfig();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", cfg.isEnabled());
        body.put("mode", cfg.getMode().name());
        body.put("profile", cfg.getProfile().name());
        body.put("maxRetries", cfg.getMaxRetries());
        body.put("retryBackoffMs", cfg.getRetryBackoffMs());
        body.put("defaultContextChars", cfg.getDefaultContextChars());
        body.put("profiles", cfg.getProfiles());
        body.put("modelScores", cfg.getModelScores());
        return body;
    }

    @PutMapping("/config")
    public Map<String, Object> updateConfig(@RequestBody RoutingConfigUpdateRequest request) {
        routingService.updateRuntimeConfig(
                request.enabled(),
                request.mode(),
                request.profile(),
                request.maxRetries(),
                request.retryBackoffMs()
        );
        return config();
    }

    @PostMapping("/simulate")
    public Map<String, Object> simulate(@RequestBody SimulateRequest request) {
        Long userId = request.userId() != null ? request.userId() : userService.requireCurrentUserId();
        String prompt = request.prompt() != null ? request.prompt() : "";
        boolean streaming = Boolean.TRUE.equals(request.streaming());
        RoutingDecision decision = routingService.decideFromPrompt(userId, prompt, streaming);
        return toDecisionBody(decision);
    }

    @GetMapping("/shadow-stats")
    public Map<String, Object> shadowStats(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "7") int days
    ) {
        Instant toInstant = to != null && !to.isBlank() ? Instant.parse(to) : Instant.now();
        Instant fromInstant = from != null && !from.isBlank()
                ? Instant.parse(from)
                : toInstant.minus(Math.max(1, days), ChronoUnit.DAYS);
        return routingService.shadowStats(fromInstant, toInstant);
    }

    private static Map<String, Object> toDecisionBody(RoutingDecision decision) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", decision.mode().name());
        body.put("profile", decision.profile().name());
        body.put("selectedModelName", decision.selectedModelName());
        body.put("selectedModelId", decision.selectedModelId());
        body.put("fallbackModelName", decision.fallbackModelName());
        body.put("selectedEstimatedCostEur", decision.selectedEstimatedCostEur());
        body.put("fallbackEstimatedCostEur", decision.fallbackEstimatedCostEur());
        body.put("promptChars", decision.promptChars());
        body.put("signals", decision.signals());
        body.put("ranked", decision.ranked());
        body.put("eliminated", decision.eliminated());
        return body;
    }

    public record RoutingConfigUpdateRequest(
            Boolean enabled,
            String mode,
            String profile,
            Integer maxRetries,
            Long retryBackoffMs
    ) {}

    public record SimulateRequest(String prompt, Long userId, Boolean streaming) {}
}
