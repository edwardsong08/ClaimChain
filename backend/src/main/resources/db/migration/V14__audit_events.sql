CREATE TABLE IF NOT EXISTS audit_events (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(64),
    actor_user_id BIGINT,
    actor_role VARCHAR(50),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    metadata_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_audit_events_actor_user
        FOREIGN KEY (actor_user_id)
        REFERENCES users (id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS ix_audit_events_entity_type_id
    ON audit_events (entity_type, entity_id);

CREATE INDEX IF NOT EXISTS ix_audit_events_actor_user_id
    ON audit_events (actor_user_id);

CREATE INDEX IF NOT EXISTS ix_audit_events_created_at
    ON audit_events (created_at);
