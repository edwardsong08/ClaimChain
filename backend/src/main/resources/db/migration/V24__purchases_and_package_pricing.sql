ALTER TABLE packages
    ADD COLUMN price_cents BIGINT NULL;

ALTER TABLE packages
    ADD COLUMN currency VARCHAR(3) NULL DEFAULT 'usd';

UPDATE packages
SET currency = 'usd'
WHERE currency IS NULL;

CREATE TABLE purchases (
    id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES packages (id) ON DELETE RESTRICT,
    buyer_user_id BIGINT NOT NULL REFERENCES users (id),
    status VARCHAR(20) NOT NULL,
    amount_cents BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    stripe_checkout_session_id VARCHAR(255) NULL UNIQUE,
    stripe_payment_intent_id VARCHAR(255) NULL UNIQUE,
    idempotency_key VARCHAR(100) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_purchases_buyer_idempotency_key
    ON purchases (buyer_user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX ix_purchases_buyer_created_at
    ON purchases (buyer_user_id, created_at DESC);

CREATE INDEX ix_purchases_package_id
    ON purchases (package_id);

CREATE TABLE purchase_events (
    id BIGSERIAL PRIMARY KEY,
    stripe_event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    payload_json TEXT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    purchase_id BIGINT NULL REFERENCES purchases (id) ON DELETE SET NULL
);

CREATE INDEX ix_purchase_events_purchase_id
    ON purchase_events (purchase_id);
