package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.config.RoutingProperties;
import com.backend.Ai_Gateway.services.MessageFeedbackService;
import com.backend.Ai_Gateway.services.UserService;
import com.backend.Ai_Gateway.services.dto.ModelQualityStats;
import com.backend.Ai_Gateway.services.routing.RoutingDecision;
import com.backend.Ai_Gateway.services.routing.RoutingJudgeService;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/routing")
@PreAuthorize("hasRole('ADMIN')")
public class RoutingController {

    private final RoutingService routingService;
    private final RoutingJudgeService routingJudgeService;
    private final UserService userService;
    private final MessageFeedbackService messageFeedbackService;

    public RoutingController(
            RoutingService routingService,
            RoutingJudgeService routingJudgeService,
            UserService userService,
            MessageFeedbackService messageFeedbackService
    ) {
        this.routingService = routingService;
        this.routingJudgeService = routingJudgeService;
        this.userService = userService;
        this.messageFeedbackService = messageFeedbackService;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        RoutingProperties cfg = routingService.effectiveConfig();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", cfg.isEnabled());
        body.put("mode", cfg.getMode().name());
        body.put("canaryPercent", cfg.getCanaryPercent());
        body.put("profile", cfg.getProfile().name());
        body.put("maxRetries", cfg.getMaxRetries());
        body.put("retryBackoffMs", cfg.getRetryBackoffMs());
        body.put("defaultContextChars", cfg.getDefaultContextChars());
        body.put("profiles", cfg.getProfiles());
        body.put("modelScores", cfg.getModelScores());
        body.put("qualityFeedbackLoop", cfg.getQualityFeedbackLoop());
        body.put("costScoreCeilingEur", cfg.getCostScoreCeilingEur());
        body.put("judge", Map.of(
                "enabled", cfg.getJudge().isEnabled(),
                "batchSize", cfg.getJudge().getBatchSize(),
                "llmCompare", cfg.getJudge().isLlmCompare(),
                "compareModel", cfg.getJudge().getCompareModel() != null ? cfg.getJudge().getCompareModel() : ""
        ));
        body.put("privacy", Map.of(
                "snapshotRetentionDays", cfg.getPrivacy().getSnapshotRetentionDays(),
                "clearSnapshotsAfterJudge", cfg.getPrivacy().isClearSnapshotsAfterJudge()
        ));
        return body;
    }

    @PutMapping("/config")
    public Map<String, Object> updateConfig(@RequestBody RoutingConfigUpdateRequest request) {
        routingService.updateRuntimeConfig(
                request.enabled(),
                request.mode(),
                request.profile(),
                request.maxRetries(),
                request.retryBackoffMs(),
                request.canaryPercent()
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

    /**
     * Phase 2: sample SHADOW disagreements and run the proposed model once vs stored applied answer.
     * Does not re-run the applied model.
     */
    @PostMapping("/judge/run")
    public Map<String, Object> runJudge(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "ALL") String mode
    ) {
        return routingJudgeService.runBatch(limit, mode);
    }

    /**
     * Empirical quality from user thumbs (Phase 1).
     * Models with no feedback are omitted; empty list means overall quality is NOT_MEASURED.
     */
    @GetMapping("/model-quality")
    public Map<String, Object> modelQuality() {
        List<ModelQualityStats> models = messageFeedbackService.aggregateByModel();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("models", models);
        body.put(
                "overallQualityStatus",
                models.isEmpty() ? ModelQualityStats.NOT_MEASURED : ModelQualityStats.MEASURED
        );
        body.put("note", "Quality is NOT_MEASURED until at least one thumbs vote exists for a model. "
                + "Never treat static RoutingProperties.ModelScore.quality as measured quality. "
                + "Feedback loop is HUMAN_IN_THE_LOOP: measured quality does not auto-update decide() scores.");
        return body;
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
            Long retryBackoffMs,
            Integer canaryPercent
    ) {}

    public record SimulateRequest(String prompt, Long userId, Boolean streaming) {}
}
