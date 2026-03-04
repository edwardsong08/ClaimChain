ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS review_started_by_user_id BIGINT;

ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS review_started_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_claims_review_started_by_user'
    ) THEN
        ALTER TABLE claims
            ADD CONSTRAINT fk_claims_review_started_by_user
            FOREIGN KEY (review_started_by_user_id)
            REFERENCES users (id)
            ON DELETE SET NULL;
    END IF;
END
$$;
