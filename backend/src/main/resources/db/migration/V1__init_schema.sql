-- USERS
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    phone VARCHAR(255),
    address VARCHAR(255),
    ein_or_license VARCHAR(255),
    business_type VARCHAR(255),
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    business_name VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email ON users(email);

-- CLAIMS
CREATE TABLE IF NOT EXISTS claims (
    id BIGSERIAL PRIMARY KEY,

    debtor_name VARCHAR(255),
    debtor_email VARCHAR(255),
    debtor_phone VARCHAR(255),

    client_name VARCHAR(255),
    client_contact VARCHAR(255),
    client_address VARCHAR(255),
    debt_type VARCHAR(255),
    contact_history TEXT,

    service_description TEXT,
    amount_owed NUMERIC(19,2),

    date_of_service DATE,
    date_of_default DATE,

    document_url TEXT,
    contract_file_key TEXT,

    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    risk_score INTEGER,

    submitted_at TIMESTAMP NOT NULL DEFAULT NOW(),

    user_id BIGINT NOT NULL
);

ALTER TABLE claims
    ADD CONSTRAINT fk_claims_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE;

-- INDEXES (common queries)
CREATE INDEX IF NOT EXISTS ix_claims_user_id ON claims(user_id);
CREATE INDEX IF NOT EXISTS ix_claims_status ON claims(status);
CREATE INDEX IF NOT EXISTS ix_claims_submitted_at ON claims(submitted_at);
