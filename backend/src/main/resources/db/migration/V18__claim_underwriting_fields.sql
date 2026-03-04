ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS debtor_address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS debtor_type VARCHAR(50) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN IF NOT EXISTS jurisdiction_state VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS claim_type VARCHAR(50) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN IF NOT EXISTS dispute_status VARCHAR(50) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS last_payment_date DATE,
    ADD COLUMN IF NOT EXISTS original_amount NUMERIC(19,2),
    ADD COLUMN IF NOT EXISTS current_amount NUMERIC(19,2);

UPDATE claims
SET current_amount = amount_owed
WHERE current_amount IS NULL
  AND amount_owed IS NOT NULL;

UPDATE claims
SET original_amount = amount_owed
WHERE original_amount IS NULL
  AND amount_owed IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_claims_debtor_type ON claims (debtor_type);
CREATE INDEX IF NOT EXISTS ix_claims_jurisdiction_state ON claims (jurisdiction_state);
CREATE INDEX IF NOT EXISTS ix_claims_claim_type ON claims (claim_type);
CREATE INDEX IF NOT EXISTS ix_claims_dispute_status ON claims (dispute_status);
CREATE INDEX IF NOT EXISTS ix_claims_date_of_default ON claims (date_of_default);
