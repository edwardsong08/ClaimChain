ALTER TABLE claim_documents
    ADD COLUMN IF NOT EXISTS extraction_status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED';

ALTER TABLE claim_documents
    ADD COLUMN IF NOT EXISTS extracted_char_count INTEGER;

ALTER TABLE claim_documents
    ADD COLUMN IF NOT EXISTS extraction_error_code VARCHAR(80);

ALTER TABLE claim_documents
    ADD COLUMN IF NOT EXISTS extraction_error_message VARCHAR(255);

UPDATE claim_documents
SET extraction_status = 'NOT_STARTED'
WHERE extraction_status IS NULL;

CREATE INDEX IF NOT EXISTS ix_claim_documents_extraction_status
    ON claim_documents (extraction_status);
