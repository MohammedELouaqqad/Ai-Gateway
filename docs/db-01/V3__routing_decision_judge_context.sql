-- Judge sampling indexes (columns created in V1).
-- Contract: judge uses applied_response_snapshot vs ONE proposed-model run (do not re-run applied).

CREATE INDEX IF NOT EXISTS idx_routing_decision_user_message
    ON routing_decision_log (user_message_id);

CREATE INDEX IF NOT EXISTS idx_routing_decision_judge_ready
    ON routing_decision_log (diverged, created_at)
    WHERE prompt_snapshot IS NOT NULL AND applied_response_snapshot IS NOT NULL;
