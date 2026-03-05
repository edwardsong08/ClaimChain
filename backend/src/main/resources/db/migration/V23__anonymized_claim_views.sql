CREATE TABLE anonymized_claim_views (
    id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES packages (id) ON DELETE CASCADE,
    claim_id BIGINT NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    jurisdiction_state VARCHAR(50) NOT NULL,
    debtor_type VARCHAR(50) NOT NULL,
    claim_type VARCHAR(50) NOT NULL,
    dispute_status VARCHAR(50) NOT NULL,
    debt_age_days INTEGER NOT NULL,
    amount_band VARCHAR(30) NOT NULL,
    score_total INTEGER NOT NULL,
    grade VARCHAR(10) NOT NULL,
    extraction_success_rate DOUBLE PRECISION NOT NULL,
    doc_types_present TEXT NOT NULL,
    UNIQUE (package_id, claim_id)
);

CREATE INDEX ix_anonymized_views_package_id
    ON anonymized_claim_views (package_id);

CREATE INDEX ix_anonymized_views_score
    ON anonymized_claim_views (score_total DESC);
