-- ForgeAI Phase 2: user accounts and the projects they own.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    -- Emails are stored lower-cased by the application, so a plain unique constraint is enough.
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE projects (
    id          UUID          PRIMARY KEY,
    owner_id    UUID          NOT NULL,
    name        VARCHAR(120)  NOT NULL,
    description VARCHAR(2000),
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Supports "list my projects, most recently updated first".
CREATE INDEX idx_projects_owner_updated ON projects (owner_id, updated_at DESC);
