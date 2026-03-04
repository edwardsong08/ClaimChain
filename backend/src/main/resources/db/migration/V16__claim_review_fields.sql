ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS review_notes TEXT;

ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS missing_docs_json TEXT;

ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS reviewed_by_user_id BIGINT;

ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_claims_reviewed_by_user'
    ) THEN
        ALTER TABLE claims
            ADD CONSTRAINT fk_claims_reviewed_by_user
            FOREIGN KEY (reviewed_by_user_id)
            REFERENCES users (id)
            ON DELETE SET NULL;
    END IF;
END
$$;
