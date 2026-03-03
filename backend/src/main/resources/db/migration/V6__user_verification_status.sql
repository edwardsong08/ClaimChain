ALTER TABLE users
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS verified_by BIGINT NULL,
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS reject_reason TEXT NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_verified_by
    FOREIGN KEY (verified_by)
    REFERENCES users (id);

UPDATE users
SET
    verification_status = 'APPROVED',
    verified_at = COALESCE(verified_at, NOW())
WHERE is_verified = TRUE
  AND verification_status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_users_verification_status
    ON users (verification_status);
