-- ForgeAI Phase 3: idea intake. One intake per project: the idea, the clarifying questions
-- Claude asked (in rounds) with the user's answers, and the resulting project brief.

CREATE TABLE idea_intakes (
    project_id    UUID         PRIMARY KEY,
    idea          TEXT         NOT NULL,
    status        VARCHAR(30)  NOT NULL,
    current_round INTEGER      NOT NULL,
    brief_json    TEXT,
    brief_model   VARCHAR(100),
    version       INTEGER      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_idea_intakes_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_idea_intakes_status CHECK (status IN ('AWAITING_ANSWERS', 'BRIEF_READY'))
);

CREATE TABLE idea_intake_questions (
    id             UUID        PRIMARY KEY,
    project_id     UUID        NOT NULL,
    round_number   INTEGER     NOT NULL,
    position_index INTEGER     NOT NULL,
    question       TEXT        NOT NULL,
    rationale      TEXT        NOT NULL,
    answer         TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_intake_questions_intake FOREIGN KEY (project_id) REFERENCES idea_intakes (project_id) ON DELETE CASCADE,
    CONSTRAINT uk_intake_questions_position UNIQUE (project_id, round_number, position_index)
);
