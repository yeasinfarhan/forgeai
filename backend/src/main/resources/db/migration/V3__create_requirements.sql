-- ForgeAI Phase 4: requirements derived from the project brief, with review workflow and history.

CREATE TABLE requirements (
    id                  UUID         PRIMARY KEY,
    project_id          UUID         NOT NULL,
    type                VARCHAR(20)  NOT NULL,
    seq_number          INTEGER      NOT NULL,
    category            VARCHAR(60),
    title               VARCHAR(200) NOT NULL,
    description         TEXT         NOT NULL,
    -- One criterion per line.
    acceptance_criteria TEXT         NOT NULL,
    priority            VARCHAR(10)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    source              VARCHAR(10)  NOT NULL,
    -- True once a person changed AI-written content; such requirements survive regeneration.
    edited              BOOLEAN      NOT NULL,
    version             INTEGER      NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_requirements_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uk_requirements_code UNIQUE (project_id, type, seq_number),
    CONSTRAINT ck_requirements_type CHECK (type IN ('FUNCTIONAL', 'NON_FUNCTIONAL')),
    CONSTRAINT ck_requirements_priority CHECK (priority IN ('MUST', 'SHOULD', 'COULD')),
    CONSTRAINT ck_requirements_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED')),
    CONSTRAINT ck_requirements_source CHECK (source IN ('AI', 'USER'))
);

CREATE INDEX idx_requirements_project ON requirements (project_id);

-- A snapshot after every change, so earlier wording is never lost.
CREATE TABLE requirement_revisions (
    id                  UUID         PRIMARY KEY,
    requirement_id      UUID         NOT NULL,
    version             INTEGER      NOT NULL,
    change_type         VARCHAR(20)  NOT NULL,
    category            VARCHAR(60),
    title               VARCHAR(200) NOT NULL,
    description         TEXT         NOT NULL,
    acceptance_criteria TEXT         NOT NULL,
    priority            VARCHAR(10)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_requirement_revisions_requirement FOREIGN KEY (requirement_id) REFERENCES requirements (id) ON DELETE CASCADE,
    CONSTRAINT uk_requirement_revisions_version UNIQUE (requirement_id, version),
    CONSTRAINT ck_requirement_revisions_change CHECK (change_type IN ('CREATED', 'EDITED', 'STATUS_CHANGED'))
);
