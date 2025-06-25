## 🔍 ClaimChain – Platform Overview

**ClaimChain** is a full-stack, production-grade platform that enables service providers to submit unpaid work claims, which are then validated, scored, grouped, and offered for purchase to vetted collection agencies or debt buyers. The system features secure authentication, document parsing, automated risk scoring, and digital packaging to streamline claim recovery and monetization.

The platform aims to reduce friction and cost for small-to-mid-size service providers while giving buyers access to vetted, risk-profiled claim portfolios.

---

## 🎯 Project Goals

- Build a **scalable, maintainable backend** using modern Java tooling (Spring Boot, JPA).
- Provide a **simple, intuitive UI** for non-technical users to submit and track claims.
- Create an **automated, anonymous portfolio marketplace** for vetted buyers to evaluate and purchase claims.
- Focus on **backend quality, modular logic**, and infrastructure first.
- Serve as a **startup prototype** with real-world legal/financial potential.

---

## ⚙️ Tech Stack

### 🔙 Backend (Java + Spring Boot)

- Spring Boot (Web, Security, Data JPA)
- PostgreSQL (via Docker)
- Apache Tika / Tesseract (file parsing & OCR)
- AWS SDK (S3 storage)
- Stripe SDK (buyer payments)
- Lombok, DevTools
- Docker (local setup)
- GitHub Actions (CI/CD – planned)
- Deployed to AWS (planned)

### 🌐 Frontend (React + Vite)

- React Router DOM
- Zustand or Context API
- Axios for HTTP
- File upload via Dropzone or input
- Tailwind CSS (optional)
- Charts via Chart.js or Recharts
- Deployed via Vercel (planned)

---

## 🧑‍💻 Local Development Setup

> These steps assume you're using **Windows with WSL2**, but will also work with Linux or macOS with slight modifications.

### 📦 Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- [Java 17+ JDK](https://adoptium.net/)
- [Maven](https://maven.apache.org/) (or use `./mvnw`)
- [DBeaver](https://dbeaver.io/) (optional, for DB GUI)
- Git

---

### ⚙️ Backend Setup

1. **Clone the Repository**

   ```bash
   git clone https://github.com/your-org/claimchain.git
   cd claimchain
   ```

2. **Start PostgreSQL via Docker Compose**

   ```bash
   docker-compose up -d
   ```

   This will spin up a PostgreSQL container with the following credentials:

   - Username: `claimchain`
   - Password: `claimchainpw`
   - Database: `claimchaindb`
   - Port: `5432`

3. **Configure **``** (already set)**

   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/claimchaindb
   spring.datasource.username=claimchain
   spring.datasource.password=claimchainpw
   spring.sql.init.mode=always
   ```

4. **Run the Backend**

   ```bash
   cd backend
   ./mvnw clean install
   ./mvnw spring-boot:run
   ```

---

### 🌐 Frontend Setup (Coming Soon)

> The frontend is React (Vite-based). Once initialized, setup instructions will appear here.

---

### 🕪 Optional: Access DB via GUI (DBeaver)

- **Host:** `localhost`
- **Port:** `5432`
- **Database:** `claimchaindb`
- **User:** `claimchain`
- **Password:** `claimchainpw`

---

## 📘️ Team Onboarding Notes

- **Dev Branching Strategy:** Use feature branches (`feature/<name>`) off `main`. Submit pull requests for review.
- **Code Style:** Use conventional commits and format code using IDE auto-format or `.editorconfig` where available.
- **Environment Isolation:** Use Docker for all services. Do not install Postgres manually.
- **Secrets & Keys:** Never commit `.env` files or API keys to version control. Use secure methods (e.g., GitHub Actions secrets, AWS Secrets Manager).
- **VS Code Recommended Extensions:** Java Extension Pack, Spring Boot Tools, Docker, ESLint (for frontend), Prettier.
- **First Contact:** Open a GitHub Issue or Slack thread if something seems broken or unclear during setup.

