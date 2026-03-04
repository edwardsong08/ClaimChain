ALTER TABLE claim_documents
    ADD COLUMN IF NOT EXISTS document_type VARCHAR(50) NOT NULL DEFAULT 'OTHER';

UPDATE claim_documents
SET document_type = 'OTHER'
WHERE document_type IS NULL;

CREATE INDEX IF NOT EXISTS ix_claim_documents_document_type
    ON claim_documents (document_type);
