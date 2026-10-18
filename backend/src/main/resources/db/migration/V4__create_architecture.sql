-- ForgeAI Phase 5: the architecture proposal of a project, with review workflow and full history.
-- One architecture per project; its content (overview, components, decisions, flows) is a JSON document.

CREATE TABLE architectures (
    project_id               UUID         PRIMARY KEY,
    status                   VARCHAR(20)  NOT NULL,
    content_json             TEXT         NOT NULL,
    model                    VARCHAR(100),
    -- True once a person changed the AI-written content.
    edited                   BOOLEAN      NOT NULL,
    -- Hash of the approved requirements the content was generated from, to detect later changes.
    requirements_fingerprint VARCHAR(64)  NOT NULL,
    revision                 INTEGER      NOT NULL,
    version                  INTEGER      NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_architectures_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_architectures_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED'))
);

-- A full snapshot after every change: generation, edit or status change.
CREATE TABLE architecture_revisions (
    id           UUID         PRIMARY KEY,
    project_id   UUID         NOT NULL,
    revision     INTEGER      NOT NULL,
    change_type  VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    content_json TEXT         NOT NULL,
    model        VARCHAR(100),
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_architecture_revisions_architecture FOREIGN KEY (project_id) REFERENCES architectures (project_id) ON DELETE CASCADE,
    CONSTRAINT uk_architecture_revisions_revision UNIQUE (project_id, revision),
    CONSTRAINT ck_architecture_revisions_change CHECK (change_type IN ('GENERATED', 'EDITED', 'STATUS_CHANGED')),
    CONSTRAINT ck_architecture_revisions_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED'))
);
