-- A user's own shortlist of library memes: the ones they want to reach again without searching.
CREATE TABLE favorite (
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    template_id UUID        NOT NULL REFERENCES meme_template (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, template_id)
);

CREATE INDEX idx_favorite_user_recent ON favorite (user_id, created_at DESC);

-- What people searched for (only short phrases that found something), so the most common ones can be
-- offered as shortcuts. Only the phrase and who typed it are kept, never the results.
CREATE TABLE search_log (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    term        TEXT        NOT NULL,
    searched_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_search_log_recent ON search_log (searched_at);
