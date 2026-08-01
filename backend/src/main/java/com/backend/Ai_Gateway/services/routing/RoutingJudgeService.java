package com.backend.Ai_Gateway.services.routing;

import com.backend.Ai_Gateway.config.RoutingProperties;
import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.enums.JudgeVerdict;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.ProviderCapabilities;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.repositories.MessageRepository;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.services.ChatService;
import com.backend.Ai_Gateway.services.ModelPricingService;
import com.backend.Ai_Gateway.services.ModelService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 2 disagreement judge.
 * Contract: compare {@link RoutingDecisionLog#getAppliedResponseSnapshot()} to ONE proposed-model run.
 * Never re-call the applied model.
 */
@Service
public class RoutingJudgeService {

    private final RoutingService routingService;
    private final RoutingDecisionLogRepository decisionLogRepository;
    private final MessageRepository messageRepository;
    private final ModelRepository modelRepository;
    private final ModelService modelService;
    private final ModelPricingService pricingService;
    private final LLMProviderRouter llmProviderRouter;
    private final MeterRegistry meterRegistry;

    public RoutingJudgeService(
            RoutingService routingService,
            RoutingDecisionLogRepository decisionLogRepository,
            MessageRepository messageRepository,
            ModelRepository modelRepository,
            ModelService modelService,
            ModelPricingService pricingService,
            LLMProviderRouter llmProviderRouter,
            MeterRegistry meterRegistry
    ) {
        this.routingService = routingService;
        this.decisionLogRepository = decisionLogRepository;
        this.messageRepository = messageRepository;
        this.modelRepository = modelRepository;
        this.modelService = modelService;
        this.pricingService = pricingService;
        this.llmProviderRouter = llmProviderRouter;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public Map<String, Object> runBatch(Integer limit, String mode) {
        RoutingProperties.Judge cfg = routingService.effectiveConfig().getJudge();
        if (!cfg.isEnabled()) {
            return Map.of("ok", false, "error", "judge_disabled");
        }
        int batch = limit != null && limit > 0 ? Math.min(limit, 50) : cfg.getBatchSize();
        String rawMode = mode != null ? mode.trim().toUpperCase(Locale.ROOT) : "ALL";
        // ALL = SHADOW + CANARY holdouts (diverged rows with snapshots)
        String modeFilter = rawMode.isEmpty() || "ALL".equals(rawMode) ? null : rawMode;

        List<RoutingDecisionLog> sample = decisionLogRepository.findJudgeReadySample(
                modeFilter, PageRequest.of(0, batch));

        int ok = 0;
        int errors = 0;
        List<Map<String, Object>> results = new ArrayList<>();
        for (RoutingDecisionLog log : sample) {
            Map<String, Object> row = judgeOne(log, cfg);
            results.add(row);
            if (Boolean.TRUE.equals(row.get("ok"))) {
                ok++;
            } else {
                errors++;
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("sampled", sample.size());
        body.put("judgedOk", ok);
        body.put("judgedError", errors);
        body.put("mode", modeFilter != null ? modeFilter : "ALL");
        body.put("results", results);
        body.put("contract", "stored_applied_vs_one_proposed_run");
        body.put("signalNote",
                "Per-case verdicts are noisy (non-deterministic LLM). Use aggregate win rates only; "
                        + "never treat a single disagreement as a go/no-go.");
        return body;
    }

    private boolean cfgClearSnapshots() {
        return routingService.effectiveConfig().getPrivacy().isClearSnapshotsAfterJudge();
    }

    private Map<String, Object> judgeOne(RoutingDecisionLog log, RoutingProperties.Judge cfg) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("decisionLogId", log.getId());
        row.put("selectedModel", log.getSelectedModelName());
        row.put("appliedModel", log.getAppliedModelName());
        try {
            Model proposed = modelRepository.findByName(log.getSelectedModelName().trim())
                    .orElseThrow(() -> new IllegalStateException("proposed_model_missing:" + log.getSelectedModelName()));
            if (!ProviderCapabilities.hasChatAdapter(proposed.getProvider().getType())) {
                throw new IllegalStateException("proposed_model_no_chat_adapter");
            }

            List<ChatMessage> messages = reconstructContext(log);
            ChatRequest request = new ChatRequest(proposed.getName(), messages);
            long started = System.currentTimeMillis();
            // ONE proposed-model run — do not call applied model
            ChatResponse proposedResponse = llmProviderRouter.chat(proposed.getProvider(), request);
            long latency = System.currentTimeMillis() - started;

            int promptTokens = proposedResponse.promptTokens() != null ? proposedResponse.promptTokens() : 0;
            int completionTokens = proposedResponse.completionTokens() != null ? proposedResponse.completionTokens() : 0;
            double proposedCost = pricingService.calculateCostEur(promptTokens, completionTokens, proposed.getName());

            String proposedText = proposedResponse.content() != null ? proposedResponse.content() : "";
            JudgeVerdict verdict = compare(
                    cfg,
                    log.getPromptSnapshot(),
                    log.getAppliedResponseSnapshot(),
                    proposedText
            );

            log.setProposedResponseSnapshot(truncate(proposedText, 8_000));
            log.setProposedPromptTokens(promptTokens);
            log.setProposedCompletionTokens(completionTokens);
            log.setProposedActualCostEur(proposedCost);
            log.setJudgeVerdict(verdict.name());
            log.setJudgedAt(Instant.now());
            log.setJudgeError(null);
            if (cfgClearSnapshots()) {
                routingService.clearSnapshots(log);
            }
            decisionLogRepository.save(log);

            meterRegistry.counter(
                    "ai_gateway_routing_judge_total",
                    "verdict", verdict.name().toLowerCase(Locale.ROOT)
            ).increment();

            row.put("ok", true);
            row.put("verdict", verdict.name());
            row.put("proposedActualCostEur", proposedCost);
            row.put("proposedLatencyMs", latency);
            row.put("appliedActualCostEur", log.getAppliedActualCostEur());
            return row;
        } catch (Exception ex) {
            // Do NOT set judgedAt on ERROR — keep row judge-ready for retry; snapshots stay until success or retention.
            log.setJudgeError(truncate(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(), 500));
            decisionLogRepository.save(log);
            meterRegistry.counter("ai_gateway_routing_judge_total", "verdict", "error").increment();
            row.put("ok", false);
            row.put("verdict", JudgeVerdict.ERROR.name());
            row.put("error", log.getJudgeError());
            row.put("retryable", true);
            return row;
        }
    }

    /**
     * Rebuild chat context from conversation history up to the user message that triggered the decision.
     * Falls back to promptSnapshot alone when history is unavailable.
     */
    List<ChatMessage> reconstructContext(RoutingDecisionLog log) {
        if (log.getConversationId() != null && log.getUserMessageId() != null) {
            List<Message> all = messageRepository.findByConversation_IdOrderByCreatedAtAscIdAsc(log.getConversationId());
            List<Message> clipped = new ArrayList<>();
            for (Message m : all) {
                if (m.isRegenerated()) {
                    continue;
                }
                clipped.add(m);
                if (m.getId().equals(log.getUserMessageId())) {
                    break;
                }
            }
            if (!clipped.isEmpty()) {
                return ChatService.fitToContextBudget(clipped);
            }
        }
        String prompt = log.getPromptSnapshot() != null ? log.getPromptSnapshot() : "";
        return List.of(new ChatMessage(MessageRole.USER, prompt));
    }

    JudgeVerdict compare(
            RoutingProperties.Judge cfg,
            String prompt,
            String applied,
            String proposed
    ) {
        String a = applied != null ? applied.trim() : "";
        String p = proposed != null ? proposed.trim() : "";
        if (p.isEmpty() && a.isEmpty()) {
            return JudgeVerdict.TIE;
        }
        if (p.isEmpty()) {
            return JudgeVerdict.APPLIED_BETTER;
        }
        if (a.isEmpty()) {
            return JudgeVerdict.PROPOSED_BETTER;
        }
        if (cfg.isLlmCompare()) {
            return llmCompare(cfg, prompt, a, p);
        }
        return heuristicCompare(a, p);
    }

    /**
     * Cheap deterministic compare — prefers substantial, non-refusal answers.
     * Not a substitute for human review; good enough to unblock Phase 2 cost/non-regret.
     */
    static JudgeVerdict heuristicCompare(String applied, String proposed) {
        String a = applied != null ? applied.trim() : "";
        String p = proposed != null ? proposed.trim() : "";
        if (p.isEmpty() && a.isEmpty()) {
            return JudgeVerdict.TIE;
        }
        if (p.isEmpty()) {
            return JudgeVerdict.APPLIED_BETTER;
        }
        if (a.isEmpty()) {
            return JudgeVerdict.PROPOSED_BETTER;
        }
        double appliedScore = answerScore(a);
        double proposedScore = answerScore(p);
        double delta = proposedScore - appliedScore;
        if (Math.abs(delta) < 0.15) {
            return JudgeVerdict.TIE;
        }
        return delta > 0 ? JudgeVerdict.PROPOSED_BETTER : JudgeVerdict.APPLIED_BETTER;
    }

    private static double answerScore(String text) {
        String t = text.trim();
        if (t.isEmpty()) {
            return 0;
        }
        double len = Math.min(t.length(), 2_000) / 2_000.0;
        boolean refusal = t.toLowerCase(Locale.ROOT).contains("i cannot")
                || t.toLowerCase(Locale.ROOT).contains("i can't")
                || t.toLowerCase(Locale.ROOT).contains("as an ai");
        double structure = (t.contains("\n") || t.contains("- ") || t.contains("1.")) ? 0.15 : 0;
        return len + structure - (refusal ? 0.4 : 0);
    }

    private JudgeVerdict llmCompare(RoutingProperties.Judge cfg, String prompt, String applied, String proposed) {
        try {
            Model compareModel = cfg.getCompareModel() != null && !cfg.getCompareModel().isBlank()
                    ? modelRepository.findByName(cfg.getCompareModel().trim()).orElse(null)
                    : null;
            if (compareModel == null) {
                compareModel = modelService.resolveActiveModel(null);
            }
            if (!ProviderCapabilities.hasChatAdapter(compareModel.getProvider().getType())) {
                return heuristicCompare(applied, proposed);
            }
            String judgePrompt = """
                    You compare two assistant answers to the same user prompt.
                    Reply with exactly one token: PROPOSED or APPLIED or TIE.

                    User prompt:
                    %s

                    APPLIED answer (what the user saw):
                    %s

                    PROPOSED answer (router's preferred model):
                    %s
                    """.formatted(truncate(prompt, 1500), truncate(applied, 2000), truncate(proposed, 2000));
            ChatResponse response = llmProviderRouter.chat(
                    compareModel.getProvider(),
                    new ChatRequest(compareModel.getName(), List.of(new ChatMessage(MessageRole.USER, judgePrompt)))
            );
            String raw = response.content() != null ? response.content().trim().toUpperCase(Locale.ROOT) : "";
            if (raw.contains("PROPOSED")) {
                return JudgeVerdict.PROPOSED_BETTER;
            }
            if (raw.contains("APPLIED")) {
                return JudgeVerdict.APPLIED_BETTER;
            }
            if (raw.contains("TIE")) {
                return JudgeVerdict.TIE;
            }
            return heuristicCompare(applied, proposed);
        } catch (RuntimeException ex) {
            return heuristicCompare(applied, proposed);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
