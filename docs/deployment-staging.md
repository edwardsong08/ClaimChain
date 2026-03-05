# ClaimChain — Staging Deployment (EC2 + RDS) v1

This document describes the **staging** deployment plan for ClaimChain using:
- **EC2** for the Spring Boot backend (jar + systemd)
- **RDS Postgres** for persistent data
- **Nginx + HTTPS** (Let’s Encrypt) as the public edge
- **Stripe Webhooks** pointing to the public staging URL

> Note: This is the “fast, low-cost, production-like” path.  
> **Future upgrade:** migrate the backend runtime from EC2 → **ECS Fargate** (containerized), while keeping RDS. The application-level architecture and DB schema remain the same.

---

## Goals & Non-goals

### Goals
- A public HTTPS staging URL for the backend (required for Stripe webhooks).
- DB persistence (RDS) with **Flyway as source of truth**.
- “No secrets in repo” — all secrets via environment variables (or AWS SSM later).
- Strict CORS (no permissive dev defaults).
- Health checks for basic ops visibility.

### Non-goals (for this slice)
- Full production hardening (WAF, multi-AZ, autoscaling, blue/green).
- Frontend deployment (Step 10).
- ECS migration (deferred until later).

---

## High-level architecture

Client → **Nginx (EC2)** → Spring Boot app (localhost:8080) → **RDS Postgres**

Stripe → Webhook → `https://<staging-host>/api/webhooks/stripe`

---

## Prerequisites

- AWS account (free-tier friendly).
- A domain name (optional but recommended; required for easy HTTPS).
- Local build passes: `cd backend && ./mvnw test`

---

## Environment variables (staging)

The backend must boot with configuration sourced from env vars only.

### Required
- `SPRING_PROFILES_ACTIVE=staging`

**Database**
- `SPRING_DATASOURCE_URL=jdbc:postgresql://<RDS-ENDPOINT>:5432/<DB_NAME>`
- `SPRING_DATASOURCE_USERNAME=<DB_USER>`
- `SPRING_DATASOURCE_PASSWORD=<DB_PASSWORD>`

**JWT**
- `JWT_SECRET=<long-random-secret>`

**Stripe**
- `STRIPE_SECRET_KEY=sk_test_...` (server-only)
- `STRIPE_WEBHOOK_SECRET=whsec_...`
- `STRIPE_SUCCESS_URL=https://<staging-host>/stripe/success` (placeholder until frontend)
- `STRIPE_CANCEL_URL=https://<staging-host>/stripe/cancel` (placeholder until frontend)

**CORS**
- `CORS_ALLOWED_ORIGINS=` (empty by default; set later when frontend exists)

### Optional
- `SERVER_PORT=8080` (default)
- `LOG_LEVEL_ROOT=INFO`

> Important: Never log secrets. Never commit secrets.

---

## Staging profile expectations (application-staging.yml)

Staging must enforce:
- `spring.jpa.hibernate.ddl-auto=validate` (Flyway owns schema)
- Flyway enabled and forward-only
- Security settings equivalent to prod posture (no relaxed defaults)
- CORS strict (no `*`)

---

## RDS Postgres setup (staging)

### Recommended settings
- Engine: Postgres (latest supported)
- Instance: free-tier eligible if available on your account
- Storage: minimal (e.g., 20 GB) with autoscaling optional
- Backups: enabled (short retention fine for staging)

### Security group rules
- RDS inbound: allow Postgres (5432) **only from EC2 security group**
- RDS not publicly accessible (recommended)

---

## EC2 setup (staging)

### Instance recommendation
- Ubuntu LTS
- free-tier eligible size if possible
- Security group inbound:
  - 22 (SSH) from your IP
  - 80 (HTTP) from anywhere
  - 443 (HTTPS) from anywhere
- Outbound: default allow (for Stripe + updates)

### Install runtime dependencies
- Java 17 runtime
- Nginx
- (Optional) certbot for Let’s Encrypt

---

## Backend deployment on EC2

### Build artifact
From local dev machine:
- `cd backend && ./mvnw package`
- Result jar typically under `backend/target/*.jar`

Copy jar to EC2 (example):
- `/opt/claimchain/claimchain-backend.jar`

### systemd service (recommended)
Create a service that:
- runs the jar
- restarts on failure
- loads environment variables from a file (not committed)

Example environment file location:
- `/etc/claimchain/claimchain.env`

> Keep `claimchain.env` readable only by root.

---

## Nginx reverse proxy (staging)

Nginx terminates TLS and proxies to localhost.

- Public: `https://<staging-host>/`
- Proxy: `http://127.0.0.1:8080`

### Required behaviors
- Preserve headers (Host, X-Forwarded-For)
- Allow Stripe webhook endpoint POSTs (no auth required on webhook endpoint, but signature is verified)
- Optional: gzip

---

## Health checks

We will add Spring Boot Actuator and expose only:
- `GET /actuator/health`

Recommended:
- In staging, expose only `health` endpoint.
- Ensure Nginx can reach it and return 200.

---

## Stripe webhooks (staging)

Once backend is accessible publicly over HTTPS:
1. Stripe Dashboard (Test Mode)
2. Developers → Webhooks → Add endpoint:
   - `https://<staging-host>/api/webhooks/stripe`
3. Select at least:
   - `checkout.session.completed`
4. Copy the signing secret `whsec_...` into:
   - `STRIPE_WEBHOOK_SECRET`

### Verification
Run an end-to-end staging test:
- Buyer checkout → creates PENDING purchase
- Complete test payment
- Webhook marks PAID + creates entitlement + package SOLD
- Buyer export endpoint works (entitlement gated)

---

## Notes on future ECS Fargate migration

The ECS migration will change *how* the backend runs, not the business logic.

When migrating:
- Build Docker image for backend
- Push to ECR
- Run ECS task with same env vars
- Replace Nginx/systemd with ALB listener + target group health checks
- Keep RDS as-is (or migrate carefully)

---

## Operational gates (staging acceptance)

Staging is considered “up” when:
- Backend boots with `SPRING_PROFILES_ACTIVE=staging`
- Flyway migrations apply cleanly
- `GET /actuator/health` returns 200 through HTTPS
- Auth works
- Buyer browse works (LISTED packages only)
- Checkout + webhook + entitlement + export flows succeed end-to-end in Stripe Test Mode