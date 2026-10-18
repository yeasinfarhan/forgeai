-- ForgeAI Phase 9: tests and code review.

-- Change sets now come in two kinds: the code of a task, or tests for the code of a task. Both are
-- reviewed file by file in the same way.
ALTER TABLE code_changesets ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'CODE';
ALTER TABLE code_changesets ADD CONSTRAINT ck_code_changesets_kind CHECK (kind IN ('CODE', 'TESTS'));

-- One AI review of the accepted code.
CREATE TABLE code_reviews (
    id               UUID          PRIMARY KEY,
    project_id       UUID          NOT NULL,
    summary          TEXT          NOT NULL,
    model            VARCHAR(100),
    files_reviewed   INTEGER       NOT NULL,
    files_total      INTEGER       NOT NULL,
    -- Fingerprint of the accepted code at review time, to tell when the code changed since.
    code_fingerprint VARCHAR(64)   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_code_reviews_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE INDEX idx_code_reviews_project ON code_reviews (project_id, created_at);

-- One finding of a review. A finding can be turned into a backlog task or dismissed.
CREATE TABLE review_findings (
    id               UUID          PRIMARY KEY,
    review_id        UUID          NOT NULL,
    position         INTEGER       NOT NULL,
    severity         VARCHAR(10)   NOT NULL,
    category         VARCHAR(20)   NOT NULL,
    path             VARCHAR(300),
    line             INTEGER,
    title            VARCHAR(200)  NOT NULL,
    detail           TEXT          NOT NULL,
    suggestion       TEXT          NOT NULL,
    requirement_code VARCHAR(20),
    task_type        VARCHAR(20)   NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    -- The task created from this finding. Kept as a code when the task is deleted.
    task_id          UUID,
    task_code        VARCHAR(10),
    version          INTEGER       NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_review_findings_review FOREIGN KEY (review_id) REFERENCES code_reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_findings_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE SET NULL,
    CONSTRAINT ck_review_findings_severity CHECK (severity IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_review_findings_status CHECK (status IN ('OPEN', 'DISMISSED', 'TASK_CREATED')),
    CONSTRAINT ck_review_findings_line CHECK (line IS NULL OR (path IS NOT NULL AND line > 0))
);

CREATE INDEX idx_review_findings_review ON review_findings (review_id);
