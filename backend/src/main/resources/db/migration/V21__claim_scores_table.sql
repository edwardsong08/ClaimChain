CREATE TABLE claim_scores (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    ruleset_id BIGINT NOT NULL REFERENCES rulesets (id),
    ruleset_version INTEGER NOT NULL,
    eligible BOOLEAN NOT NULL,
    score_total INTEGER NOT NULL,
    grade VARCHAR(10) NOT NULL,
    subscore_enforceability INTEGER,
    subscore_documentation INTEGER,
    subscore_collectability INTEGER,
    subscore_operational_risk INTEGER,
    explainability_json TEXT NOT NULL,
    feature_snapshot_json TEXT NOT NULL,
    scored_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    scored_by_user_id BIGINT NULL REFERENCES users (id)
);

CREATE INDEX ix_claim_scores_claim_id_scored_at
    ON claim_scores (claim_id, scored_at DESC);

CREATE INDEX ix_claim_scores_ruleset_id
    ON claim_scores (ruleset_id);
