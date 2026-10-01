-- Memes belong to the user who asked for them.
ALTER TABLE meme ADD COLUMN owner_id UUID NOT NULL;

-- A generation job is a request waiting in (or moving through) a queue.
-- Workers claim PENDING rows with FOR UPDATE SKIP LOCKED, so several workers
-- and several application instances can share the queue safely.
CREATE TABLE generation_job (
    id             UUID PRIMARY KEY,
    requester_id   UUID        NOT NULL REFERENCES app_user (id),
    situation      TEXT        NOT NULL,
    status         TEXT        NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    attempts       INT         NOT NULL DEFAULT 0,
    failure_reason TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ
);

CREATE INDEX idx_generation_job_pending ON generation_job (created_at) WHERE status = 'PENDING';
CREATE INDEX idx_generation_job_running ON generation_job (started_at) WHERE status = 'RUNNING';

CREATE TABLE generation_job_result (
    job_id   UUID NOT NULL REFERENCES generation_job (id) ON DELETE CASCADE,
    position INT  NOT NULL,
    meme_id  UUID NOT NULL REFERENCES meme (id),
    PRIMARY KEY (job_id, position)
);
