-- Every run of the vision model for a report is remembered, so the number of runs per day can be capped
-- for the whole site and a meme is not looked at again too soon, however its reports come and go.
CREATE TABLE review_run (
    id          BIGSERIAL   PRIMARY KEY,
    template_id UUID        NOT NULL REFERENCES meme_template (id) ON DELETE CASCADE,
    ran_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_review_run_recent ON review_run (ran_at);
CREATE INDEX idx_review_run_template ON review_run (template_id, ran_at DESC);

-- An administrator who asks for a new look skips the budget and the cooldown.
ALTER TABLE report_review ADD COLUMN forced BOOLEAN NOT NULL DEFAULT FALSE;

-- Who closed a report (a person or the automatic rules) and why.
ALTER TABLE meme_report
    ADD COLUMN resolved_by     TEXT CHECK (resolved_by IN ('ADMIN', 'AUTO')),
    ADD COLUMN resolution_note TEXT;

-- Existing closed reports were closed by an administrator.
UPDATE meme_report SET resolved_by = 'ADMIN' WHERE status = 'RESOLVED';

CREATE INDEX idx_meme_report_user_recent ON meme_report (user_id, created_at);
CREATE INDEX idx_meme_report_auto ON meme_report (resolved_at) WHERE resolved_by = 'AUTO';

-- Every time a description is replaced because of reports, what it was and what it became, so that
-- an automatic change can be taken back with one click.
CREATE TABLE profile_change (
    id          UUID PRIMARY KEY,
    template_id UUID        NOT NULL REFERENCES meme_template (id) ON DELETE CASCADE,
    before      JSONB       NOT NULL,
    after       JSONB       NOT NULL,
    source      TEXT        NOT NULL CHECK (source IN ('ADMIN', 'AUTO')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    undone_at   TIMESTAMPTZ
);

CREATE INDEX idx_profile_change_template ON profile_change (template_id, created_at DESC);
