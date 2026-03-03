-- Demo users (password = Password123! hashed with BCrypt)
-- NOTE: These are DEV ONLY seeds.

INSERT INTO users (name, email, password, role, is_verified, business_name)
VALUES
  ('Demo Provider', 'provider_demo@claimchain.dev', '$2b$10$1S5qztaUkt4byLgeCJylOO5oK1gI0MgwUFtF2/xZwCAFnESUgLK4G', 'SERVICE_PROVIDER', TRUE, 'Demo Roofing LLC'),
  ('Demo Agency', 'agency_demo@claimchain.dev', '$2b$10$1S5qztaUkt4byLgeCJylOO5oK1gI0MgwUFtF2/xZwCAFnESUgLK4G', 'COLLECTION_AGENCY', TRUE, 'Demo Collections Inc.')
ON CONFLICT (email) DO NOTHING;

-- Demo claims for the provider (assumes provider is id=1 if empty DB; uses lookup by email to be safe)
INSERT INTO claims (
  debtor_name, debtor_email, debtor_phone,
  client_name, client_contact, client_address,
  debt_type, contact_history,
  service_description, amount_owed,
  date_of_service, date_of_default,
  status, risk_score, submitted_at,
  user_id
)
SELECT
  'John Doe', 'johndoe@example.com', '123-456-7890',
  'Jane Client', 'jane.client@example.com', '123 Main St, Newark, NJ',
  'CONSUMER', 'Email reminders sent 2x',
  'Roof repair service', 1500.00,
  DATE '2025-01-10', DATE '2025-02-10',
  'PENDING', NULL, NOW(),
  u.id
FROM users u
WHERE u.email = 'provider_demo@claimchain.dev'
ON CONFLICT DO NOTHING;
