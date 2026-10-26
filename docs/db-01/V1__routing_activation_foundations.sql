-- Routing activation foundations.
-- Flyway runs BEFORE Hibernate, so this script must CREATE tables that may not exist yet.
-- Additive ALTERs use IF NOT EXISTS for DBs already shaped by ddl-auto.
-- Note: high SHADOW divergence is EXPECTED (winner ≠ default), not a quality failure.

CREATE TABLE IF NOT EXISTS message_feedback (
    id              BIGSERIAL PRIMARY KEY,
    message_id      BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    rating          VARCHAR(16)  NOT NULL,
    reason          VARCHAR(500),
    model_name      VARCHAR(100),
    source          VARCHAR(32)  NOT NULL DEFAULT 'EXPLICIT',
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_message_feedback_message_user UNIQUE (message_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_message_feedback_user ON message_feedback (user_id);
CREATE INDEX IF NOT EXISTS idx_message_feedback_model ON message_feedback (model_name);
CREATE INDEX IF NOT EXISTS idx_message_feedback_source ON message_feedback (source);

-- Ensure source column exists on DBs created before this field
ALTER TABLE message_feedback ADD COLUMN IF NOT EXISTS source VARCHAR(32);
UPDATE message_feedback SET source = 'EXPLICIT' WHERE source IS NULL;

CREATE TABLE IF NOT EXISTS routing_decision_log (
    id                              BIGSERIAL PRIMARY KEY,
    created_at                      TIMESTAMPTZ,
    user_id                         BIGINT,
    mode                            VARCHAR(32),
    profile                         VARCHAR(32),
    selected_model_name             VARCHAR(255),
    applied_model_name              VARCHAR(255),
    fallback_model_name             VARCHAR(255),
    selected_estimated_cost_eur     DOUBLE PRECISION NOT NULL DEFAULT 0,
    applied_estimated_cost_eur      DOUBLE PRECISION NOT NULL DEFAULT 0,
    diverged                        BOOLEAN NOT NULL DEFAULT FALSE,
    prompt_chars                    INTEGER NOT NULL DEFAULT 0,
    conversation_id                 BIGINT,
    user_message_id                 BIGINT,
    applied_assistant_message_id    BIGINT,
    prompt_snapshot                 TEXT,
    applied_response_snapshot       TEXT,
    ranked_json                     VARCHAR(2000),
    eliminated_json                 VARCHAR(2000)
);

-- Additive columns for legacy tables created earlier by Hibernate
ALTER TABLE routing_decision_log ADD COLUMN IF NOT EXISTS conversation_id BIGINT;
ALTER TABLE routing_decision_log ADD COLUMN IF NOT EXISTS user_message_id BIGINT;
ALTER TABLE routing_decision_log ADD COLUMN IF NOT EXISTS applied_assistant_message_id BIGINT;
ALTER TABLE routing_decision_log ADD COLUMN IF NOT EXISTS prompt_snapshot TEXT;
ALTER TABLE routing_decision_log ADD COLUMN IF NOT EXISTS applied_response_snapshot TEXT;

CREATE INDEX IF NOT EXISTS idx_routing_decision_diverged
    ON routing_decision_log (diverged, created_at);

CREATE INDEX IF NOT EXISTS idx_routing_decision_conversation
    ON routing_decision_log (conversation_id);

-- message.regenerated / superseded_by_message_id: added by Hibernate ddl-auto from Message entity
-- (message table does not exist yet when Flyway runs on a fresh DB).
