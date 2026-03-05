CREATE TABLE packages (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_user_id BIGINT NOT NULL REFERENCES users (id),
    ruleset_id BIGINT NULL REFERENCES rulesets (id),
    ruleset_version INTEGER NULL,
    total_claims INTEGER NOT NULL DEFAULT 0,
    total_face_value NUMERIC(19,2) NOT NULL DEFAULT 0,
    notes TEXT NULL
);

CREATE INDEX ix_packages_status
    ON packages (status);

CREATE INDEX ix_packages_created_at
    ON packages (created_at DESC);

CREATE TABLE package_claims (
    id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES packages (id) ON DELETE CASCADE,
    claim_id BIGINT NOT NULL REFERENCES claims (id),
    included_reason_json TEXT NOT NULL DEFAULT '{}',
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    added_by_user_id BIGINT NOT NULL REFERENCES users (id),
    UNIQUE (package_id, claim_id)
);

CREATE INDEX ix_package_claims_package_id
    ON package_claims (package_id);

CREATE INDEX ix_package_claims_claim_id
    ON package_claims (claim_id);

CREATE TABLE buyer_entitlements (
    id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES packages (id) ON DELETE CASCADE,
    buyer_user_id BIGINT NOT NULL REFERENCES users (id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (package_id, buyer_user_id)
);
