CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Write model: MemeTemplate -------------------------------------------------

CREATE TABLE meme_template (
    id              UUID PRIMARY KEY,
    name            TEXT        NOT NULL,
    image_key       TEXT        NOT NULL,
    image_width     INT         NOT NULL CHECK (image_width > 0),
    image_height    INT         NOT NULL CHECK (image_height > 0),
    status          TEXT        NOT NULL CHECK (status IN ('DRAFT', 'APPROVED', 'RETIRED')),
    version         INT         NOT NULL DEFAULT 1,
    meaning         TEXT        NOT NULL DEFAULT '',
    usage_examples  TEXT[]      NOT NULL DEFAULT '{}',
    emotions        TEXT[]      NOT NULL DEFAULT '{}',
    aliases         TEXT[]      NOT NULL DEFAULT '{}',
    license_note    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A template may have zero slots (image already carries its own text).
CREATE TABLE template_slot (
    template_id UUID    NOT NULL REFERENCES meme_template (id) ON DELETE CASCADE,
    slot_no     INT     NOT NULL,
    role        TEXT    NOT NULL,
    max_chars   INT     NOT NULL CHECK (max_chars > 0),
    required    BOOLEAN NOT NULL DEFAULT TRUE,
    x           INT     NOT NULL,
    y           INT     NOT NULL,
    width       INT     NOT NULL CHECK (width > 0),
    height      INT     NOT NULL CHECK (height > 0),
    PRIMARY KEY (template_id, slot_no)
);

-- Write model: Meme ---------------------------------------------------------
-- A meme references its template by id + version and keeps a snapshot of the
-- slots it was composed against, so later template revisions never break it.

CREATE TABLE meme (
    id               UUID PRIMARY KEY,
    template_id      UUID        NOT NULL,
    template_version INT         NOT NULL,
    slot_snapshot    JSONB       NOT NULL DEFAULT '[]',
    status           TEXT        NOT NULL CHECK (status IN ('COMPOSED', 'KEPT')),
    image_key        TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE meme_caption (
    meme_id UUID NOT NULL REFERENCES meme (id) ON DELETE CASCADE,
    slot_no INT  NOT NULL,
    text    TEXT NOT NULL,
    PRIMARY KEY (meme_id, slot_no)
);

CREATE INDEX idx_meme_template ON meme (template_id);

-- Read model: template search (hybrid: vector + trigram keyword) -------------
-- Rebuilt from template events; never written to by the domain directly.

CREATE TABLE template_search (
    template_id UUID PRIMARY KEY,
    search_text TEXT         NOT NULL,
    embedding   VECTOR(1024) NOT NULL,
    slot_layout JSONB        NOT NULL DEFAULT '[]'
);

CREATE INDEX idx_template_search_embedding
    ON template_search USING hnsw (embedding vector_cosine_ops);

-- Trigram index instead of tsvector: the 'simple' text search config does not
-- segment Chinese, but trigrams still match substrings.
CREATE INDEX idx_template_search_text
    ON template_search USING gin (search_text gin_trgm_ops);
