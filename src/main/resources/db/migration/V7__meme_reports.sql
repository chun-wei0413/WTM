-- Users tell the administrators when a meme's description or tags do not fit it.
CREATE TABLE meme_report (
    id          UUID PRIMARY KEY,
    template_id UUID        NOT NULL REFERENCES meme_template (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    reason      TEXT        NOT NULL CHECK (reason IN ('WRONG_TAGS', 'WRONG_MEANING', 'INAPPROPRIATE', 'OTHER')),
    comment     TEXT        NOT NULL DEFAULT '',
    status      TEXT        NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    resolution  TEXT        CHECK (resolution IN ('APPLIED', 'DISMISSED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);

-- A person has at most one open report per meme; reporting again changes what they wrote.
CREATE UNIQUE INDEX uq_meme_report_open ON meme_report (user_id, template_id) WHERE status = 'OPEN';
CREATE INDEX idx_meme_report_open ON meme_report (template_id) WHERE status = 'OPEN';

-- The vision model looks at a reported meme again, with the complaints in hand, and proposes a new
-- description. One row per meme: it is both the line of memes waiting to be looked at (claimed with
-- FOR UPDATE SKIP LOCKED, like tagging) and where the proposal is kept for the administrator.
CREATE TABLE report_review (
    template_id UUID PRIMARY KEY REFERENCES meme_template (id) ON DELETE CASCADE,
    status      TEXT        NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    attempts    INT         NOT NULL DEFAULT 0,
    proposal    JSONB,
    last_error  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at  TIMESTAMPTZ,
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_report_review_pending ON report_review (created_at) WHERE status = 'PENDING';
CREATE INDEX idx_report_review_running ON report_review (started_at) WHERE status = 'RUNNING';
