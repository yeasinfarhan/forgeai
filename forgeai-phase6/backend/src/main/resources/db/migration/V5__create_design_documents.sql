-- ForgeAI Phase 6: database design and API design. Both are JSON documents with the same review
-- workflow and history, so they share one table, told apart by kind.

CREATE TABLE design_documents (
    id                UUID         PRIMARY KEY,
    project_id        UUID         NOT NULL,
    kind              VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    content_json      TEXT         NOT NULL,
    model             VARCHAR(100),
    -- True once a person changed the AI-written content.
    edited            BOOLEAN      NOT NULL,
    -- Hash of the inputs (earlier stages) the content was generated from, to detect later changes.
    input_fingerprint VARCHAR(64)  NOT NULL,
    revision          INTEGER      NOT NULL,
    version           INTEGER      NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_design_documents_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uk_design_documents_kind UNIQUE (project_id, kind),
    CONSTRAINT ck_design_documents_kind CHECK (kind IN ('DATABASE', 'API')),
    CONSTRAINT ck_design_documents_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED'))
);

-- A full snapshot after every change: generation, edit or status change.
CREATE TABLE design_document_revisions (
    id           UUID         PRIMARY KEY,
    document_id  UUID         NOT NULL,
    revision     INTEGER      NOT NULL,
    change_type  VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    content_json TEXT         NOT NULL,
    model        VARCHAR(100),
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_design_revisions_document FOREIGN KEY (document_id) REFERENCES design_documents (id) ON DELETE CASCADE,
    CONSTRAINT uk_design_revisions_revision UNIQUE (document_id, revision),
    CONSTRAINT ck_design_revisions_change CHECK (change_type IN ('GENERATED', 'EDITED', 'STATUS_CHANGED')),
    CONSTRAINT ck_design_revisions_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED'))
);
