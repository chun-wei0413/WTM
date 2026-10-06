-- An explanation of a meme written by someone other than the vision model, kept with where it came from.
-- Sources such as Wikipedia describe a meme in words people wrote, so the model can be told what the
-- picture is instead of guessing, and the page can credit the text (CC BY-SA asks for that).
ALTER TABLE meme_template
    ADD COLUMN reference_text    TEXT,
    ADD COLUMN reference_source  TEXT,
    ADD COLUMN reference_url     TEXT,
    ADD COLUMN reference_license TEXT;
