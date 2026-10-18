-- ForgeAI Phase 11: one row per AI call, for per-user limits and the usage page.
CREATE TABLE ai_usage (
    id            UUID          PRIMARY KEY,
    user_id       UUID          NOT NULL,
    -- The project the call was made for; kept (without a foreign key) when the project is deleted.
    project_id    UUID,
    purpose       VARCHAR(60)   NOT NULL,
    provider      VARCHAR(20)   NOT NULL,
    model         VARCHAR(100),
    input_tokens  INTEGER       NOT NULL,
    output_tokens INTEGER       NOT NULL,
    -- SUCCESS, or the failure reason (RATE_LIMITED, TIMEOUT, ...).
    outcome       VARCHAR(30)   NOT NULL,
    duration_ms   INTEGER       NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_usage_user_created ON ai_usage (user_id, created_at);
