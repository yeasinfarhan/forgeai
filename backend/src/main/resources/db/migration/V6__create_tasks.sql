-- ForgeAI Phase 7: the development backlog. Tasks are generated from the approved design and
-- tracked on a board; every change is logged.

-- One row per project with a generated backlog: what it was generated from.
CREATE TABLE task_backlogs (
    project_id        UUID         PRIMARY KEY,
    -- Hash of the approved design the AI tasks were generated from, to detect later changes.
    input_fingerprint VARCHAR(64)  NOT NULL,
    model             VARCHAR(100),
    generated_at      TIMESTAMPTZ  NOT NULL,
    version           INTEGER      NOT NULL,
    CONSTRAINT fk_task_backlogs_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE TABLE tasks (
    id           UUID          PRIMARY KEY,
    project_id   UUID          NOT NULL,
    -- Shown as T-001; never reused within a project.
    seq_number   INTEGER       NOT NULL,
    -- Order in the backlog (0 = first).
    position     INTEGER       NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    description  TEXT          NOT NULL,
    type         VARCHAR(20)   NOT NULL,
    estimate     INTEGER,
    status       VARCHAR(20)   NOT NULL,
    source       VARCHAR(10)   NOT NULL,
    -- True once a person changed AI-written content; such tasks survive regeneration.
    edited       BOOLEAN       NOT NULL,
    -- Acceptance criteria, dependencies and links to requirements and design elements (JSON).
    details_json TEXT          NOT NULL,
    version      INTEGER       NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uk_tasks_code UNIQUE (project_id, seq_number),
    CONSTRAINT ck_tasks_type CHECK (type IN ('SETUP', 'DATABASE', 'BACKEND', 'FRONTEND', 'TESTING', 'DEVOPS', 'DOCUMENTATION')),
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    CONSTRAINT ck_tasks_source CHECK (source IN ('AI', 'USER')),
    CONSTRAINT ck_tasks_estimate CHECK (estimate IS NULL OR estimate IN (1, 2, 3, 5, 8, 13))
);

CREATE INDEX idx_tasks_project ON tasks (project_id);

-- Activity log per task.
CREATE TABLE task_events (
    id          UUID         PRIMARY KEY,
    task_id     UUID         NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20),
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_task_events_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT ck_task_events_type CHECK (type IN ('CREATED', 'EDITED', 'STATUS_CHANGED'))
);

CREATE INDEX idx_task_events_task ON task_events (task_id);
