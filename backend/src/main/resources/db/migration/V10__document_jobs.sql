CREATE TABLE IF NOT EXISTS document_jobs (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    job_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,

    CONSTRAINT fk_document_jobs_document
        FOREIGN KEY (document_id)
        REFERENCES claim_documents (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_document_jobs_document_id
    ON document_jobs (document_id);

CREATE INDEX IF NOT EXISTS idx_document_jobs_status
    ON document_jobs (status);

CREATE INDEX IF NOT EXISTS idx_document_jobs_job_type
    ON document_jobs (job_type);
