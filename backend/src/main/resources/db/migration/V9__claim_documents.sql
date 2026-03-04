CREATE TABLE IF NOT EXISTS claim_documents (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    sniffed_content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_claim_documents_claim
        FOREIGN KEY (claim_id)
        REFERENCES claims (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_claim_documents_uploaded_by_user
        FOREIGN KEY (uploaded_by_user_id)
        REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_claim_documents_claim_id
    ON claim_documents (claim_id);

CREATE INDEX IF NOT EXISTS idx_claim_documents_uploaded_by_user_id
    ON claim_documents (uploaded_by_user_id);

CREATE INDEX IF NOT EXISTS idx_claim_documents_status
    ON claim_documents (status);
