-- The site is first of all a library of memes collected from elsewhere, found again by describing
-- them. A library entry is a meme_template (usually without caption slots); these columns record
-- what was found in the image, where it came from and what it looks like, so the same picture is
-- never collected twice.

ALTER TABLE meme_template
    ADD COLUMN image_text       TEXT        NOT NULL DEFAULT '',
    ADD COLUMN tags             TEXT[]      NOT NULL DEFAULT '{}',
    ADD COLUMN source_type      TEXT,
    ADD COLUMN source_url       TEXT,
    ADD COLUMN source_page_url  TEXT,
    ADD COLUMN attribution      TEXT,
    ADD COLUMN content_sha256   TEXT,
    ADD COLUMN phash            BIGINT,
    ADD COLUMN collected_at     TIMESTAMPTZ;

-- Exactly the same bytes can only be in the library once.
CREATE UNIQUE INDEX uq_meme_template_sha256 ON meme_template (content_sha256) WHERE content_sha256 IS NOT NULL;

-- Where a picture is waiting to be looked at by the vision model. Workers claim PENDING rows with
-- FOR UPDATE SKIP LOCKED, the same way generation jobs are claimed.
CREATE TABLE template_tagging (
    template_id UUID PRIMARY KEY REFERENCES meme_template (id) ON DELETE CASCADE,
    status      TEXT        NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    attempts    INT         NOT NULL DEFAULT 0,
    last_error  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at  TIMESTAMPTZ,
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_template_tagging_pending ON template_tagging (created_at) WHERE status = 'PENDING';
CREATE INDEX idx_template_tagging_running ON template_tagging (started_at) WHERE status = 'RUNNING';

-- One row per "collect from this source" request, with what happened to each picture it found.
CREATE TABLE collection_run (
    id          UUID PRIMARY KEY,
    source      TEXT        NOT NULL,
    options     TEXT        NOT NULL DEFAULT '',
    status      TEXT        NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    found       INT         NOT NULL DEFAULT 0,
    imported    INT         NOT NULL DEFAULT 0,
    duplicates  INT         NOT NULL DEFAULT 0,
    rejected    INT         NOT NULL DEFAULT 0,
    failed      INT         NOT NULL DEFAULT 0,
    message     TEXT,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);
