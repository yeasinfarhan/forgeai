-- ForgeAI Phase 8: code generation. The AI proposes a change set per task; a person reviews every
-- file (diff) and accepts or rejects it. Accepted files form the project's code workspace.

-- The accepted code of a project, one row per file.
CREATE TABLE code_files (
    id             UUID          PRIMARY KEY,
    project_id     UUID          NOT NULL,
    path           VARCHAR(300)  NOT NULL,
    content        TEXT          NOT NULL,
    -- Task whose change set last wrote the file (e.g. T-004).
    last_task_code VARCHAR(10),
    version        INTEGER       NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_code_files_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uk_code_files_path UNIQUE (project_id, path)
);

-- One AI proposal for one task.
CREATE TABLE code_changesets (
    id           UUID          PRIMARY KEY,
    project_id   UUID          NOT NULL,
    -- Kept when the task is deleted, so the history stays readable.
    task_id      UUID,
    task_code    VARCHAR(10)   NOT NULL,
    task_title   VARCHAR(200)  NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    summary      TEXT          NOT NULL,
    model        VARCHAR(100),
    version      INTEGER       NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_code_changesets_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_code_changesets_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE SET NULL,
    CONSTRAINT ck_code_changesets_status CHECK (status IN ('PENDING', 'COMPLETED', 'DISCARDED'))
);

CREATE INDEX idx_code_changesets_project ON code_changesets (project_id);
CREATE INDEX idx_code_changesets_task ON code_changesets (task_id);

-- One proposed file change. base_content is the accepted content when the change was proposed,
-- so accepting can detect that the file changed in the meantime.
CREATE TABLE code_changes (
    id           UUID          PRIMARY KEY,
    changeset_id UUID          NOT NULL,
    position     INTEGER       NOT NULL,
    path         VARCHAR(300)  NOT NULL,
    action       VARCHAR(10)   NOT NULL,
    base_content TEXT,
    new_content  TEXT,
    explanation  TEXT          NOT NULL,
    decision     VARCHAR(10)   NOT NULL,
    decided_at   TIMESTAMPTZ,
    CONSTRAINT fk_code_changes_changeset FOREIGN KEY (changeset_id) REFERENCES code_changesets (id) ON DELETE CASCADE,
    CONSTRAINT uk_code_changes_path UNIQUE (changeset_id, path),
    CONSTRAINT ck_code_changes_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT ck_code_changes_decision CHECK (decision IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_code_changes_content CHECK ((action = 'DELETE') = (new_content IS NULL))
);
