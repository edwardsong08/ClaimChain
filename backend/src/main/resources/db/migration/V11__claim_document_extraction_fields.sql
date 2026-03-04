ALTER TABLE claim_documents
    ADD COLUMN IF NOT EXISTS extracted_storage_key VARCHAR(512),
    ADD COLUMN IF NOT EXISTS extracted_at TIMESTAMPTZ;
