package com.backend.Ai_Gateway.services.routing;

import com.backend.Ai_Gateway.config.GuardrailsProperties;
import com.backend.Ai_Gateway.config.RoutingProperties;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.provider.ProviderCapabilities;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import com.backend.Ai_Gateway.services.ModelPricingService;
import com.backend.Ai_Gateway.services.UsageGuardService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Décision de routing (filtre dur → score soft). Pur : n'appelle pas de LLM.
 * <p>
 * Quality feedback is {@code HUMAN_IN_THE_LOOP}: thumbs/judge accumulate evidence but do
 * <strong>not</strong> auto-write {@link RoutingProperties#scoreFor(String)} quality scores.
 * <p>
 * Cost score uses an absolute band (catalog/config ceiling), not survivors-only max — so
 * disabling one expensive model does not reshuffle others. Free models (€0) still score 100
 * on cost; under ECONOMY that intentionally prefers local/free when available.
 * <p>
 * Scale note (KISS): each {@link #decide} hits the DB (budget SUMs + findAll models). Fine at
 * low volume; consider a short TTL cache later — do not optimize prematurely.
 */
@Service
public class RoutingService {

    private final RoutingProperties defaults;
    private final AtomicReference<RoutingProperties> runtime;
    private final ModelRepository modelRepository;
    private final UsageLogRepository usageLogRepository;
    private final ModelPricingService pricingService;
    private final UsageGuardService usageGuardService;
    private final MeterRegistry meterRegistry;
    private final RoutingDecisionLogRepository decisionLogRepository;

    public RoutingService(
            RoutingProperties defaults,
            ModelRepository modelRepository,
            UsageLogRepository usageLogRepository,
            ModelPricingService pricingService,
            UsageGuardService usageGuardService,
            MeterRegistry meterRegistry,
            RoutingDecisionLogRepository decisionLogRepository
    ) {
        this.defaults = defaults;
        this.runtime = new AtomicReference<>(copyProps(defaults));
        this.modelRepository = modelRepository;
        this.usageLogRepository = usageLogRepository;
        this.pricingService = pricingService;
        this.usageGuardService = usageGuardService;
        this.meterRegistry = meterRegistry;
        this.decisionLogRepository = decisionLogRepository;
    }

    public RoutingProperties effectiveConfig() {
        return runtime.get();
    }

    public void updateRuntimeConfig(
            Boolean enabled,
            String mode,
            String profile,
            Integer maxRetries,
            Long retryBackoffMs,
            Integer canaryPercent
    ) {
        RoutingProperties current = copyProps(runtime.get());
        if (enabled != null) {
            current.setEnabled(enabled);
        }
        if (mode != null && !mode.isBlank()) {
            current.setMode(mode);
        }
        if (profile != null && !profile.isBlank()) {
            current.setProfile(profile);
        }
        if (maxRetries != null) {
            current.setMaxRetries(maxRetries);
        }
        if (retryBackoffMs != null) {
            current.setRetryBackoffMs(retryBackoffMs);
        }
        if (canaryPercent != null) {
            current.setCanaryPercent(canaryPercent);
        }
        runtime.set(current);
    }

    public void resetRuntimeToDefaults() {
        runtime.set(copyProps(defaults));
    }

    /**
     * @param promptChars taille estimée du prompt (proxy faible)
     * @param streaming   si true, exige un adaptateur stream
     */
    public RoutingDecision decide(Long userId, int promptChars, boolean streaming) {
        RoutingProperties cfg = effectiveConfig();
        GuardrailsProperties guard = usageGuardService.effectiveConfig();

        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);
        double userSpent = usageLogRepository.sumEstimatedCostByUserIdAndCreatedAtBetween(userId, dayStart, dayEnd);
        double globalSpent = usageLogRepository.sumEstimatedCostBetween(dayStart, dayEnd);
        double userRemaining = Math.max(0, guard.getBudget().getUserPerDayEur() - userSpent);
        double globalRemaining = Math.max(0, guard.getBudget().getGlobalPerDayEur() - globalSpent);

        Model fallback = modelRepository.findByIsDefaultTrue().orElse(null);
        String fallbackName = fallback != null ? fallback.getName() : null;

        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("promptChars", promptChars);
        signals.put("userBudgetRemainingEur", round4(userRemaining));
        signals.put("globalBudgetRemainingEur", round4(globalRemaining));
        signals.put("streaming", streaming);

        List<RoutingDecision.EliminatedCandidate> eliminated = new ArrayList<>();
        List<Model> survivors = new ArrayList<>();

        List<Model> allModels = modelRepository.findAll();
        for (Model model : allModels) {
            String reason = hardFilterReason(model, promptChars, streaming, cfg, guard, userRemaining, globalRemaining);
            if (reason != null) {
                eliminated.add(new RoutingDecision.EliminatedCandidate(
                        model.getId(),
                        model.getName(),
                        model.getProvider() != null ? model.getProvider().getName() : null,
                        reason
                ));
            } else {
                survivors.add(model);
            }
        }

        if (survivors.isEmpty()) {
            return RoutingDecision.empty(cfg.getMode(), cfg.getProfile(), fallbackName, promptChars, signals, eliminated);
        }

        int assumedCompletion = guard.getEstimate().getAssumedCompletionTokens();
        int charsPerToken = Math.max(1, guard.getEstimate().getCharsPerToken());
        int promptTokens = Math.max(1, promptChars / charsPerToken);

        Map<Long, Double> costs = new HashMap<>();
        for (Model model : survivors) {
            double cost = pricingService.calculateCostEur(promptTokens, assumedCompletion, model.getName());
            costs.put(model.getId(), cost);
        }

        // Absolute cost band: ceiling from config or full catalog (not survivors-only).
    // Disabled models still count toward the ceiling. Deleting a model from the DB
    // (vs disabling it) will shrink the ceiling — set cost-score-ceiling-eur for a fixed band.
    double costCeiling = cfg.getCostScoreCeilingEur();
        if (costCeiling <= 0) {
            for (Model model : allModels) {
                double cost = pricingService.calculateCostEur(promptTokens, assumedCompletion, model.getName());
                costCeiling = Math.max(costCeiling, cost);
            }
        }
        signals.put("costScoreCeilingEur", round4(costCeiling));
        signals.put("costScoreBasis", "ABSOLUTE_BAND");
        signals.put("qualityFeedbackLoop", cfg.getQualityFeedbackLoop());
        // Free (€0) → costScore 100 by design; ECONOMY therefore prefers local/free when present.
        signals.put("freeModelsCostScore", 100.0);

        RoutingProperties.ProfileWeights weights = cfg.weightsFor(cfg.getProfile());
        List<RoutingDecision.RankedCandidate> ranked = new ArrayList<>();

        for (Model model : survivors) {
            double cost = costs.getOrDefault(model.getId(), 0.0);
            double costScore;
            if (cost <= 0) {
                costScore = 100.0;
            } else if (costCeiling <= 0) {
                costScore = 100.0;
            } else {
                costScore = (1.0 - Math.min(1.0, cost / costCeiling)) * 100.0;
            }
            // Static preference scores — NOT auto-updated from thumbs/judge (human-in-the-loop).
            RoutingProperties.ModelScore ms = cfg.scoreFor(model.getName());
            double qualityScore = clamp(ms.getQuality());
            double speedScore = clamp(ms.getSpeed());
            double total = weights.getCost() * costScore
                    + weights.getQuality() * qualityScore
                    + weights.getSpeed() * speedScore;

            Map<String, Double> detail = new LinkedHashMap<>();
            detail.put("costWeight", weights.getCost());
            detail.put("qualityWeight", weights.getQuality());
            detail.put("speedWeight", weights.getSpeed());
            detail.put("costScore", round2(costScore));
            detail.put("qualityScore", round2(qualityScore));
            detail.put("speedScore", round2(speedScore));
            detail.put("totalScore", round2(total));
            detail.put("costScoreCeilingEur", round4(costCeiling));

            ranked.add(new RoutingDecision.RankedCandidate(
                    model.getId(),
                    model.getName(),
                    model.getProvider() != null ? model.getProvider().getName() : null,
                    round4(cost),
                    round2(costScore),
                    round2(qualityScore),
                    round2(speedScore),
                    round2(total),
                    detail
            ));
        }

        ranked.sort(Comparator
                .comparingDouble(RoutingDecision.RankedCandidate::totalScore).reversed()
                .thenComparing(RoutingDecision.RankedCandidate::modelName));

        RoutingDecision.RankedCandidate winner = ranked.get(0);
        double fallbackCost = fallbackName != null
                ? pricingService.calculateCostEur(promptTokens, assumedCompletion, fallbackName)
                : 0;

        return new RoutingDecision(
                cfg.getMode(),
                cfg.getProfile(),
                winner.modelName(),
                winner.modelId(),
                fallbackName,
                winner.estimatedCostEur(),
                round4(fallbackCost),
                promptChars,
                List.copyOf(ranked),
                List.copyOf(eliminated),
                signals
        );
    }

    public RoutingDecision decideFromPrompt(Long userId, String prompt, boolean streaming) {
        int chars = prompt == null ? 0 : prompt.length();
        return decide(userId, chars, streaming);
    }

    public RoutingDecision decideFromMessages(Long userId, List<ChatMessage> messages, boolean streaming) {
        int chars = 0;
        if (messages != null) {
            for (ChatMessage m : messages) {
                if (m != null && m.content() != null) {
                    chars += m.content().length();
                }
            }
        }
        return decide(userId, chars, streaming);
    }

    /**
     * Whether the ranked winner should drive the live LLM call.
     * CANARY is sticky by conversationId (fallback userId) so a chat does not flip mid-thread.
     * Bucket is {@code floorMod(hash(key), 100) < percent} — raising percent is monotone
     * (conversations already live stay live).
     */
    public boolean shouldApplyLiveWinner(Long conversationId, Long userId) {
        RoutingProperties cfg = effectiveConfig();
        if (cfg.getMode() == RoutingProperties.Mode.ACTIVE) {
            return true;
        }
        if (cfg.getMode() != RoutingProperties.Mode.CANARY) {
            return false;
        }
        int pct = cfg.getCanaryPercent();
        if (pct <= 0) {
            return false;
        }
        if (pct >= 100) {
            return true;
        }
        long stickyKey = conversationId != null ? conversationId : (userId != null ? userId : 0L);
        int bucket = Math.floorMod(Long.hashCode(stickyKey), 100);
        return bucket < pct;
    }

    /**
     * Persist decision with prompt snapshot + message links (Phase 2 judge prerequisite).
     * @return decision log id for later attachAppliedResponse
     */
    public Long persistAndMeter(
            RoutingDecision decision,
            Long userId,
            String appliedModelName,
            boolean diverged,
            String promptSnapshot,
            Long conversationId,
            Long userMessageId
    ) {
        RoutingDecisionLog log = new RoutingDecisionLog();
        log.setCreatedAt(Instant.now());
        log.setUserId(userId);
        log.setMode(decision.mode().name());
        log.setProfile(decision.profile().name());
        log.setSelectedModelName(decision.selectedModelName());
        log.setAppliedModelName(appliedModelName);
        log.setFallbackModelName(decision.fallbackModelName());
        log.setSelectedEstimatedCostEur(decision.selectedEstimatedCostEur());
        log.setAppliedEstimatedCostEur(
                appliedModelName != null
                        ? estimateCostForModel(appliedModelName, decision.promptChars())
                        : 0
        );
        log.setDiverged(diverged);
        log.setPromptChars(decision.promptChars());
        // GDPR: persist prompt text only for disagreements we may judge (diverged), not every decision.
        log.setPromptSnapshot(diverged ? truncate(promptSnapshot, 8_000) : null);
        log.setConversationId(conversationId);
        log.setUserMessageId(userMessageId);
        log.setRankedJson(toCompactRanked(decision.ranked()));
        log.setEliminatedJson(toCompactEliminated(decision.eliminated()));
        decisionLogRepository.save(log);

        String mode = decision.mode().name().toLowerCase(Locale.ROOT);
        String selected = decision.selectedModelName() != null ? decision.selectedModelName() : "none";
        meterRegistry.counter("ai_gateway_routing_decisions_total", "mode", mode, "model", sanitize(selected)).increment();
        if (diverged) {
            meterRegistry.counter("ai_gateway_routing_shadow_divergence_total").increment();
        }
        if (cfgModeIsCanary()) {
            meterRegistry.counter(
                    "ai_gateway_routing_canary_total",
                    "live",
                    String.valueOf(!diverged && decision.hasWinner()
                            && appliedModelName != null
                            && appliedModelName.equals(decision.selectedModelName()))
            ).increment();
        }
        return log.getId();
    }

    /** Attach applied assistant answer — judge must reuse this instead of re-calling the applied model. */
    public void attachAppliedResponse(Long decisionLogId, Long assistantMessageId, String appliedResponse) {
        attachAppliedResponse(decisionLogId, assistantMessageId, appliedResponse, null, null);
    }

    /**
     * Persist applied answer + real token cost. When applied == selected (canary/ACTIVE live),
     * also records proposedActualCostEur (real proposed-path cost without a second LLM call).
     */
    public void attachAppliedResponse(
            Long decisionLogId,
            Long assistantMessageId,
            String appliedResponse,
            Integer promptTokens,
            Integer completionTokens
    ) {
        if (decisionLogId == null) {
            return;
        }
        decisionLogRepository.findById(decisionLogId).ifPresent(log -> {
            log.setAppliedAssistantMessageId(assistantMessageId);
            // Store applied answer text only when diverged (judge baseline); skip otherwise (privacy).
            if (log.isDiverged()) {
                log.setAppliedResponseSnapshot(truncate(appliedResponse, 8_000));
            }
            if (promptTokens != null || completionTokens != null) {
                int prompt = promptTokens != null ? promptTokens : 0;
                int completion = completionTokens != null ? completionTokens : 0;
                double actual = pricingService.calculateCostEur(prompt, completion, log.getAppliedModelName());
                log.setAppliedActualCostEur(actual);
                // Live proposed path (canary/ACTIVE): applied model IS the selected winner
                if (log.getAppliedModelName() != null
                        && log.getAppliedModelName().equals(log.getSelectedModelName())) {
                    log.setProposedActualCostEur(actual);
                    log.setProposedPromptTokens(prompt);
                    log.setProposedCompletionTokens(completion);
                    if (log.isDiverged() && log.getProposedResponseSnapshot() == null) {
                        log.setProposedResponseSnapshot(truncate(appliedResponse, 8_000));
                    }
                }
            }
            decisionLogRepository.save(log);
        });
    }

    /** Clear prompt/response snapshots older than retention (GDPR / storage). */
    @Transactional
    public int purgeExpiredSnapshots() {
        int days = effectiveConfig().getPrivacy().getSnapshotRetentionDays();
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        return decisionLogRepository.clearSnapshotsOlderThan(cutoff);
    }

    /** Clear text snapshots on a judged row (verdict/costs kept). */
    public void clearSnapshots(RoutingDecisionLog log) {
        if (log == null) {
            return;
        }
        log.setPromptSnapshot(null);
        log.setAppliedResponseSnapshot(null);
        log.setProposedResponseSnapshot(null);
    }

    private boolean cfgModeIsCanary() {
        return effectiveConfig().getMode() == RoutingProperties.Mode.CANARY;
    }

    public void meterFallback(String reason) {
        meterRegistry.counter("ai_gateway_routing_fallbacks_total", "reason", sanitize(reason)).increment();
    }

    public Map<String, Object> shadowStats(Instant from, Instant to) {
        List<RoutingDecisionLog> logs = decisionLogRepository.findByCreatedAtBetweenAndMode(from, to, "SHADOW");
        // Also include CANARY holdout (diverged) for observability
        List<RoutingDecisionLog> canaryLogs = decisionLogRepository.findByCreatedAtBetweenAndMode(from, to, "CANARY");
        long total = logs.size();
        long diverged = logs.stream().filter(RoutingDecisionLog::isDiverged).count();
        double appliedCost = logs.stream().mapToDouble(RoutingDecisionLog::getAppliedEstimatedCostEur).sum();
        double selectedCost = logs.stream().mapToDouble(RoutingDecisionLog::getSelectedEstimatedCostEur).sum();

        Map<String, Long> selectedDistribution = new LinkedHashMap<>();
        for (RoutingDecisionLog log : logs) {
            String name = log.getSelectedModelName() != null ? log.getSelectedModelName() : "none";
            selectedDistribution.merge(name, 1L, Long::sum);
        }

        long canaryLive = canaryLogs.stream().filter(l -> !l.isDiverged()).count();
        long canaryHoldout = canaryLogs.stream().filter(RoutingDecisionLog::isDiverged).count();
        long judgeReady = logs.stream()
                .filter(RoutingDecisionLog::isDiverged)
                .filter(l -> l.getPromptSnapshot() != null && !l.getPromptSnapshot().isBlank())
                .filter(l -> l.getAppliedResponseSnapshot() != null && !l.getAppliedResponseSnapshot().isBlank())
                .filter(l -> l.getJudgedAt() == null)
                .count();

        List<RoutingDecisionLog> withRealPair = logs.stream()
                .filter(l -> l.getAppliedActualCostEur() != null && l.getProposedActualCostEur() != null)
                .toList();
        double realizedApplied = withRealPair.stream()
                .mapToDouble(RoutingDecisionLog::getAppliedActualCostEur)
                .sum();
        double realizedProposed = withRealPair.stream()
                .mapToDouble(RoutingDecisionLog::getProposedActualCostEur)
                .sum();

        long judged = logs.stream().filter(l -> l.getJudgedAt() != null).count();
        long proposedWins = logs.stream().filter(l -> "PROPOSED_BETTER".equals(l.getJudgeVerdict())).count();
        long appliedWins = logs.stream().filter(l -> "APPLIED_BETTER".equals(l.getJudgeVerdict())).count();
        long ties = logs.stream().filter(l -> "TIE".equals(l.getJudgeVerdict())).count();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from.toString());
        body.put("to", to.toString());
        body.put("totalDecisions", total);
        body.put("divergences", diverged);
        body.put("divergencePct", total == 0 ? 0 : round2(100.0 * diverged / total));
        body.put("divergenceInterpretation", "EXPECTED_IN_SHADOW");
        body.put("divergenceNote",
                "In SHADOW, divergence means the router would have picked a different model than the default. "
                        + "A high rate is normal and is NOT a quality failure by itself.");
        body.put("appliedCostEur", round4(appliedCost));
        body.put("selectedCostEur", round4(selectedCost));
        body.put("potentialSavingEur", round4(appliedCost - selectedCost));
        body.put("potentialSavingNote",
                "Estimate-only (pre-call pricing). Real proposed-path cost appears after canary/ACTIVE live "
                        + "or after a Phase 2 judge run.");
        body.put("realizedAppliedCostEur", round4(realizedApplied));
        body.put("realizedProposedCostEur", round4(realizedProposed));
        body.put("realizedSavingEur", round4(realizedApplied - realizedProposed));
        body.put("realizedCostPairs", withRealPair.size());
        body.put("selectedModelDistribution", selectedDistribution);
        body.put("canaryLive", canaryLive);
        body.put("canaryHoldout", canaryHoldout);
        body.put("judgeReadyDisagreements", judgeReady);
        body.put("judgedCount", judged);
        body.put("judgeProposedWins", proposedWins);
        body.put("judgeAppliedWins", appliedWins);
        body.put("judgeTies", ties);
        body.put("judgeSignalNote",
                "Judge verdicts are statistical (one noisy LLM run per case). Trust aggregates, not a single row. "
                        + "Divergence rate is a volume gauge, not a red flag.");
        body.put("qualityFeedbackLoop", effectiveConfig().getQualityFeedbackLoop());
        body.put("qualityFeedbackNote",
                "Thumbs and judge do NOT auto-update model-scores.quality used by decide(). "
                        + "A human reviews evidence and adjusts YAML / runtime scores.");
        return body;
    }

    public Model resolveModelEntity(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return null;
        }
        return modelRepository.findByName(modelName.trim()).orElse(null);
    }

    public List<Model> rankedModels(RoutingDecision decision) {
        List<Model> models = new ArrayList<>();
        if (decision == null) {
            return models;
        }
        for (RoutingDecision.RankedCandidate c : decision.ranked()) {
            modelRepository.findById(c.modelId()).ifPresent(models::add);
        }
        return models;
    }

    private String hardFilterReason(
            Model model,
            int promptChars,
            boolean streaming,
            RoutingProperties cfg,
            GuardrailsProperties guard,
            double userRemaining,
            double globalRemaining
    ) {
        if (!model.isEnabled()) {
            return "MODEL_DISABLED";
        }
        if (model.getProvider() == null) {
            return "PROVIDER_MISSING";
        }
        if (!model.getProvider().isEnabled()) {
            return "PROVIDER_INACTIVE";
        }
        var type = model.getProvider().getType();
        boolean adapterOk = streaming
                ? ProviderCapabilities.hasStreamAdapter(type)
                : ProviderCapabilities.hasChatAdapter(type);
        if (!adapterOk) {
            return streaming ? "NO_STREAM_ADAPTER" : "NO_CHAT_ADAPTER";
        }
        int window = cfg.contextCharsFor(model.getName());
        if (promptChars > window) {
            return "CONTEXT_TOO_SMALL:" + window;
        }
        if (pricingService.isBillable(model.getName())) {
            int charsPerToken = Math.max(1, guard.getEstimate().getCharsPerToken());
            int promptTokens = Math.max(1, promptChars / charsPerToken);
            int completionEst = guard.getEstimate().getAssumedCompletionTokens();
            double estimated = pricingService.calculateCostEur(promptTokens, completionEst, model.getName());
            if (estimated > userRemaining) {
                return "BUDGET_USER_INSUFFICIENT";
            }
            if (estimated > globalRemaining) {
                return "BUDGET_GLOBAL_INSUFFICIENT";
            }
        }
        return null;
    }

    private double estimateCostForModel(String modelName, int promptChars) {
        GuardrailsProperties guard = usageGuardService.effectiveConfig();
        int charsPerToken = Math.max(1, guard.getEstimate().getCharsPerToken());
        int promptTokens = Math.max(1, promptChars / charsPerToken);
        return pricingService.calculateCostEur(
                promptTokens,
                guard.getEstimate().getAssumedCompletionTokens(),
                modelName
        );
    }

    private static String toCompactRanked(List<RoutingDecision.RankedCandidate> ranked) {
        StringBuilder sb = new StringBuilder();
        for (RoutingDecision.RankedCandidate c : ranked) {
            if (!sb.isEmpty()) sb.append(';');
            sb.append(c.modelName()).append('=').append(c.totalScore());
        }
        return sb.toString();
    }

    private static String toCompactEliminated(List<RoutingDecision.EliminatedCandidate> eliminated) {
        StringBuilder sb = new StringBuilder();
        for (RoutingDecision.EliminatedCandidate c : eliminated) {
            if (!sb.isEmpty()) sb.append(';');
            sb.append(c.modelName()).append(':').append(c.reason());
        }
        return sb.toString();
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private static double clamp(double v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }

    static RoutingProperties copyProps(RoutingProperties src) {
        RoutingProperties copy = new RoutingProperties();
        copy.setEnabled(src.isEnabled());
        copy.setMode(src.getMode());
        copy.setCanaryPercent(src.getCanaryPercent());
        copy.setProfile(src.getProfile());
        copy.setMaxRetries(src.getMaxRetries());
        copy.setRetryBackoffMs(src.getRetryBackoffMs());
        copy.setDefaultContextChars(src.getDefaultContextChars());
        copy.setContextWindows(new HashMap<>(src.getContextWindows()));
        Map<String, RoutingProperties.ModelScore> scores = new HashMap<>();
        src.getModelScores().forEach((k, v) -> {
            RoutingProperties.ModelScore ms = new RoutingProperties.ModelScore();
            ms.setQuality(v.getQuality());
            ms.setSpeed(v.getSpeed());
            scores.put(k, ms);
        });
        copy.setModelScores(scores);
        Map<String, RoutingProperties.ProfileWeights> profiles = new LinkedHashMap<>();
        src.getProfiles().forEach((k, v) ->
                profiles.put(k, new RoutingProperties.ProfileWeights(v.getCost(), v.getQuality(), v.getSpeed())));
        copy.setProfiles(profiles);
        RoutingProperties.Judge judge = new RoutingProperties.Judge();
        judge.setEnabled(src.getJudge().isEnabled());
        judge.setBatchSize(src.getJudge().getBatchSize());
        judge.setLlmCompare(src.getJudge().isLlmCompare());
        judge.setCompareModel(src.getJudge().getCompareModel());
        copy.setJudge(judge);
        RoutingProperties.Privacy privacy = new RoutingProperties.Privacy();
        privacy.setSnapshotRetentionDays(src.getPrivacy().getSnapshotRetentionDays());
        privacy.setClearSnapshotsAfterJudge(src.getPrivacy().isClearSnapshotsAfterJudge());
        copy.setPrivacy(privacy);
        copy.setCostScoreCeilingEur(src.getCostScoreCeilingEur());
        copy.setQualityFeedbackLoop(src.getQualityFeedbackLoop());
        return copy;
    }
}
