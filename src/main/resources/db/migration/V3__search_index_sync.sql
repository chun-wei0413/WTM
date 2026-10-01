-- Search index entries remember which version of the template they were built
-- from. The sync job compares this with meme_template.updated_at, so a template
-- that changed while its embedding was being computed is detected as stale.
ALTER TABLE template_search
    ADD COLUMN source_updated_at TIMESTAMPTZ NOT NULL DEFAULT 'epoch',
    ADD COLUMN indexed_at        TIMESTAMPTZ NOT NULL DEFAULT now();
