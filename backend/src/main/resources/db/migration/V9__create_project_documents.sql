-- ForgeAI Phase 10: documentation. The README and the developer guide are written by the AI and
-- can be edited; the other documents of the project package are derived from the design on export.
CREATE TABLE project_documents (
    id                UUID          PRIMARY KEY,
    project_id        UUID          NOT NULL,
    kind              VARCHAR(20)   NOT NULL,
    content           TEXT          NOT NULL,
    model             VARCHAR(100),
    -- Fingerprint of the design and code the document was generated from.
    input_fingerprint VARCHAR(64)   NOT NULL,
    edited            BOOLEAN       NOT NULL,
    generated_at      TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           INTEGER       NOT NULL,
    CONSTRAINT fk_project_documents_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uk_project_documents_kind UNIQUE (project_id, kind),
    CONSTRAINT ck_project_documents_kind CHECK (kind IN ('README', 'DEVELOPER_GUIDE'))
);
