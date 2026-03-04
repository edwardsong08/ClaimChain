ALTER TABLE document_jobs
    ADD COLUMN IF NOT EXISTS next_run_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_document_jobs_next_run_at
    ON document_jobs (next_run_at);
