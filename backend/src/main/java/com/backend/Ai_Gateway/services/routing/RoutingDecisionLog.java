package com.backend.Ai_Gateway.services.routing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "routing_decision_log",
        indexes = {
                @Index(name = "idx_routing_decision_diverged", columnList = "diverged, createdAt"),
                @Index(name = "idx_routing_decision_conversation", columnList = "conversationId"),
                @Index(name = "idx_routing_decision_user_message", columnList = "userMessageId"),
                @Index(name = "idx_routing_decision_judged", columnList = "judgedAt")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class RoutingDecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant createdAt;
    private Long userId;
    private String mode;
    private String profile;
    private String selectedModelName;
    private String appliedModelName;
    private String fallbackModelName;
    /** Pre-call estimate for router winner. */
    private double selectedEstimatedCostEur;
    /** Pre-call estimate for model actually used. */
    private double appliedEstimatedCostEur;
    private boolean diverged;
    private int promptChars;

    /** Conversation after prepare — required for sticky canary + Phase 2 judge. */
    private Long conversationId;
    private Long userMessageId;
    private Long appliedAssistantMessageId;

    /**
     * Truncated prompt text for disagreement judging.
     * Without this, Phase 2 cannot re-run the proposed model on the same case.
     */
    @Column(columnDefinition = "TEXT")
    private String promptSnapshot;

    /**
     * Assistant answer actually returned to the user (applied model).
     * Judge must compare proposed-model output to THIS — do not re-call the applied model.
     */
    @Column(columnDefinition = "TEXT")
    private String appliedResponseSnapshot;

    /** Real € from applied-model token usage (canary/ACTIVE live, or SHADOW default run). */
    private Double appliedActualCostEur;

    /**
     * Real € of the proposed (selected) path.
     * Set when canary/ACTIVE already ran the winner, or after a Phase 2 judge proposed-model run.
     */
    private Double proposedActualCostEur;

    @Column(columnDefinition = "TEXT")
    private String proposedResponseSnapshot;

    private Integer proposedPromptTokens;
    private Integer proposedCompletionTokens;

    /** PROPOSED_BETTER | APPLIED_BETTER | TIE | ERROR */
    @Column(length = 32)
    private String judgeVerdict;

    private Instant judgedAt;

    @Column(length = 500)
    private String judgeError;

    @Column(length = 2000)
    private String rankedJson;

    @Column(length = 2000)
    private String eliminatedJson;

    public boolean isJudgeReady() {
        return diverged
                && promptSnapshot != null && !promptSnapshot.isBlank()
                && appliedResponseSnapshot != null && !appliedResponseSnapshot.isBlank()
                && judgedAt == null
                && selectedModelName != null && !selectedModelName.isBlank();
    }
}
