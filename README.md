# 🔍 ClaimChain — Enterprise Claims Exchange Platform

ClaimChain is a full-stack, production-grade platform that enables **service providers** to submit unpaid work claims with supporting documentation, undergo **verification and review**, receive **explainable risk scoring**, and package claims into **anonymized portfolios** that vetted **collection agencies / debt buyers** can purchase through a secure marketplace.

ClaimChain is designed as an end-to-end workflow system: **identity + verification**, **claim intake**, **secure document ingestion (parsing/OCR)**, **risk scoring (rules + ML)**, **portfolio packaging**, **payments**, **auditability**, and **multi-client access** (web + mobile).

> **Live Demo:** [ADD LINK]  
> **Demo Video:** [ADD LINK]  
> **Roadmap / Project Board:** [ADD LINK]  
> **Screenshots:** [ADD FOLDER OR LINKS]

---

## ✨ Product Highlights

- **Multi-role platform:** Service Provider, Collection Agency/Buyer, Admin
- **Secure claim intake:** validated structured submission + evidence uploads
- **Document pipeline:** S3-backed storage + async parsing/OCR + job tracking
- **Explainable scoring:** deterministic rules engine + ML augmentation (versioned + safe fallback)
- **Marketplace loop:** package creation → listing → buyer purchase → post-purchase export
- **Enterprise controls:** RBAC + ownership checks, auditable event trails, idempotent payments, and operational visibility
- **Web + Mobile:** dashboards for all roles; mobile companion app for providers (capture/upload/status)

---

## 👥 Roles & Workflows

### Service Provider
- Register & login
- Account verification workflow (admin approval required for submissions)
- Submit claims with validation
- Upload documents (PDF/images) via secure evidence uploader
- Track claim lifecycle timeline (statuses + review notes)
- View scoring results + explanations and document completeness signals
- Mobile companion app: submit claims, camera capture, upload, status updates, notifications

### Collection Agency / Buyer
- Register & login (verification/approval as required)
- Browse packages/portfolios with filters + analytics
- Secure checkout + purchase
- View purchase history
- Download/export deliverables (access-controlled and time-bound)

### Admin
- Secure admin bootstrap + admin dashboard
- Verify/reject users (with reason codes)
- Review claim queue, request missing docs, approve/reject claims with notes
- Manage lifecycle transitions (packaged/listed/sold)
- Manage packages/listings
- View audit log for all key actions
- Monitor document processing pipeline jobs (failures/retries/status)

---

## 🔁 Claim Lifecycle

Claims follow a guarded state machine with enforced transitions:

`SUBMITTED → UNDER_REVIEW → APPROVED / REJECTED → PACKAGED → LISTED → SOLD`

Admins control review decisions and workflow transitions. Providers have read access to the timeline and decision rationale.

---

## 📄 Document Upload & Processing

### Secure Evidence Upload
- Claim documents are stored in **S3-compatible storage**
- Upload safety controls:
  - file size limits
  - MIME sniffing + extension checks
  - allowed-type whitelist
  - secure download (streamed and/or signed URLs)
  - malware scanning hook (pluggable)

### Async Processing Pipeline
Documents are processed asynchronously with job tracking:
- parsing via **Apache Tika** (PDF/text extraction)
- OCR support for scanned documents (configurable)
- job states: `QUEUED / RUNNING / FAILED / DONE`
- retries + failure reason capture
- derived signals:
  - document completeness score
  - extraction confidence indicators
  - structured fields (where applicable)

---

## 📊 Risk Scoring (Rules + ML)

ClaimChain provides **explainable scoring** designed for trust and auditability.

### Rules Engine (Baseline)
- Produces:
  - `riskScore` (numeric)
  - `grade` (tier/category)
  - **explainability payload** (reason codes + weighted factors)
- Triggered after document processing and/or admin review events
- Deterministic and stable (auditable)

### ML Augmentation (Optional, Additive)
- Enhances prediction and portfolio ranking without replacing the rules engine
- Training data strategy based on platform outcomes and/or admin-reviewed labels
- Feature sources (examples):
  - claim metadata
  - document completeness/confidence
  - extracted text signals
- Includes:
  - model versioning
  - safe fallback to rules-only scoring
  - monitoring hooks for drift/quality

> **Note:** ML is intentionally implemented as an *augmentation layer* to preserve explainability and safety.

---

## 🧺 Packaging, Anonymization & Marketplace

### Portfolio Packaging
- Approved claims are aggregated into portfolios/packages
- Portfolio analytics include:
  - totals and distributions (amounts, scores, grades)
  - completeness metrics
  - summary breakdowns useful to buyers

### Anonymization & PII Controls
Portfolios are anonymized using explicit, documented rules:
- sensitive fields removed/hashed/generalized
- document exports are redacted/scrubbed according to policy
- “minimum necessary” principle for buyer deliverables
- audit logs track every export and access event

### Marketplace & Purchase Flow
- buyer browsing with filtering and analytics
- Stripe-based checkout
- post-purchase access-controlled deliverables

---

## 💳 Payments (Stripe)

Payments are implemented with production-grade handling:
- idempotency keys for purchase endpoints
- purchase ledger and state transitions
- Stripe webhook processing for:
  - successful payments
  - failures/cancellations
  - refunds (if enabled)
- audit events recorded for purchase + export actions

---

## 🔐 Security, Privacy & Compliance Posture

ClaimChain is designed with an enterprise-first security model:

### Authentication & Session Lifecycle
- JWT-based authentication
- refresh tokens + rotation/invalidation
- logout invalidation
- rate limiting on auth endpoints

### Authorization
- Role-based access control (Provider / Buyer / Admin)
- Ownership checks:
  - providers can only access their own claims/docs
  - buyers can only access purchased portfolios/exports
- Guarded lifecycle transitions (admin-only state changes)

### Secrets & Configuration
- secrets stored outside code (env/config)
- environment profiles (dev/staging/prod)
- no hardcoded secrets committed to repo

### Auditability
- append-only audit events for critical actions:
  - claim submissions
  - uploads/downloads/exports
  - admin review decisions
  - status transitions
  - purchases and webhook events
- correlation IDs for request tracing

> **Important:** ClaimChain is a portfolio project and not legal/compliance advice. Any real-world deployment would require a full compliance review and jurisdiction-specific requirements.

---

## 🧱 Architecture

**High-level components:**
- **Web client (Next.js)** for Provider/Buyer/Admin dashboards
- **Mobile app** for Provider companion workflow
- **Spring Boot API** for auth, claims, documents, scoring, marketplace
- **PostgreSQL** for relational data (users, claims, docs, jobs, packages, purchases, audits)
- **S3-compatible storage** for documents and exports
- **Background jobs** for document ingestion and scoring triggers
- **Stripe** for checkout + webhook-driven payment reconciliation

> **Architecture Diagram:** [ADD IMAGE/LINK]

---

## ⚙️ Tech Stack

### Backend
- Java 17
- Spring Boot (Web, Security, Data JPA, Validation)
- PostgreSQL
- JWT Auth + RBAC + ownership checks
- Apache Tika + OCR support
- AWS S3-compatible storage
- Stripe payments + webhooks
- Background job processing + retries
- Audit logging + request correlation

### Frontend (Web)
- TypeScript + Next.js
- Role-based routing and dashboards
- Form validation + file upload UX
- Data tables, filtering, analytics visualizations

### Mobile
- Cross-platform mobile client (Android + iOS)
- Secure token storage (keystore/keychain)
- Document capture/upload workflow
- Push notifications (optional)

### Infrastructure
- Docker-based local development environment
- CI pipeline (GitHub Actions)
- Cloud deployment architecture (AWS-ready: RDS + S3 + application hosting)
- Observability: structured logs, metrics, job monitoring

---

## 🚀 Local Development

### Prerequisites
- Java 17+
- Docker + Docker Compose
- Node.js (for web client)
- (Optional) Mobile tooling (React Native or Flutter toolchain)

### Environment Variables
Configure environment variables for:
- database connection
- JWT secrets + expirations
- S3 credentials + bucket
- Stripe keys + webhook secret
- email provider (for forgot-password) *(if applicable)*

---

## ✅ Testing & CI

### Testing Strategy
- Unit tests for services and scoring logic
- Integration tests for:
  - auth lifecycle (login/refresh/logout)
  - RBAC + ownership policy enforcement (401/403)
  - claim workflow state transitions
  - document upload + processing pipeline
  - Stripe webhook reconciliation

### CI Pipeline
- build + test on every PR
- formatting/lint checks (if enabled)
- optional: coverage reporting and artifact builds

---

## 📦 Deployment

ClaimChain is designed to deploy cleanly on AWS:
- Backend hosting (container or VM)
- RDS (PostgreSQL)
- S3 for documents and deliverables
- Environment separation (dev/staging/prod)
- Secrets management (e.g., AWS Secrets Manager)
- Backups/restore plan for DB

> **Deployment Notes:** [ADD LINK OR SECTION]

---

## 📚 Documentation

- API docs: [ADD LINK] *(Swagger/OpenAPI recommended)*
- Data model (ERD): [ADD LINK/IMAGE]
- Threat model / security notes: [ADD LINK]
- Scoring model notes: [ADD LINK]

---

## 🧾 License
[ADD LICENSE]