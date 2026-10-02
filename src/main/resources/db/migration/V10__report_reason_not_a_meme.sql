-- A new reason for a report: the picture is not a meme at all (a photo, a screenshot, a one-off joke).
ALTER TABLE meme_report DROP CONSTRAINT IF EXISTS meme_report_reason_check;
ALTER TABLE meme_report ADD CONSTRAINT meme_report_reason_check
    CHECK (reason IN ('WRONG_TAGS', 'WRONG_MEANING', 'NOT_A_MEME', 'INAPPROPRIATE', 'OTHER'));
