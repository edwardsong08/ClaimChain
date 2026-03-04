CREATE TABLE IF NOT EXISTS rulesets (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version INT NOT NULL,
    config_json TEXT NOT NULL,
    created_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activated_by_user_id BIGINT,
    activated_at TIMESTAMPTZ,
    CONSTRAINT fk_rulesets_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES users (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_rulesets_activated_by_user
        FOREIGN KEY (activated_by_user_id)
        REFERENCES users (id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS ix_rulesets_type_status
    ON rulesets (type, status);

CREATE INDEX IF NOT EXISTS ix_rulesets_type_version
    ON rulesets (type, version);

CREATE UNIQUE INDEX IF NOT EXISTS ux_rulesets_one_active_per_type
    ON rulesets (type)
    WHERE status = 'ACTIVE';
