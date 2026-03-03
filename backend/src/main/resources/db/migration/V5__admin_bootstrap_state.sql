CREATE TABLE IF NOT EXISTS admin_bootstrap_state (
    id INTEGER PRIMARY KEY,
    used_at TIMESTAMPTZ NULL,
    used_by_user_id BIGINT NULL,

    CONSTRAINT fk_admin_bootstrap_state_used_by_user
        FOREIGN KEY (used_by_user_id)
        REFERENCES users (id)
);

INSERT INTO admin_bootstrap_state (id, used_at, used_by_user_id)
VALUES (1, NULL, NULL)
ON CONFLICT (id) DO NOTHING;
