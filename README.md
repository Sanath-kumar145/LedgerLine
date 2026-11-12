# LedgerLine — Money Movement With a Ledger You Can Prove

> **Snovasys Hackathon Project — Final Working MVP**
> **Status:** Production-Ready MVP Completed (Atomic Internal Transfers, Double-Entry Postings, Deterministic Account Locking Order, Database-Enforced Idempotency, Append-Only Ledger Immutability, Dual Transaction Boundary Auditing, React 19 Banking Dashboard, 105 Passing Tests Against Real MySQL 8.0 via Testcontainers)
> **Repository:** [https://github.com/Sanath-kumar145/LedgerLine.git](https://github.com/Sanath-kumar145/LedgerLine.git)

---

## 1. Project Overview

**LedgerLine** is an enterprise-grade financial ledger platform designed around a foundational principle: **Balances are never arbitrated by loose increments; they are governed by double-entry ledger journals with strict atomicity, row-level locking, and continuous mathematical reconciliation.**

Unlike typical banking applications that mutate balances via simple database increments (`UPDATE accounts SET balance = balance + ?`), LedgerLine records every movement of value as an immutable double-entry journal containing balanced debits and credits. Every account balance is directly provable and reconcilable against its underlying ledger history.

### Core Guarantees
- **Conservation of Money:** In every financial transaction, total debits equal total credits ($\sum \text{Debits} == \sum \text{Credits}$).
- **Overdraft Immunity:** Account balances are strictly prevented from dropping below zero via three independent defense rings: application validation, pessimistic row locking with fresh reads, and database-level `CHECK (balance >= 0.00)` constraints.
- **Deadlock-Free Concurrency:** Bidirectional transfers between accounts acquire pessimistic row locks in strictly sorted natural ID order ($\min(\text{src}, \text{dst})$ then $\max(\text{src}, \text{dst})$), eliminating circular-wait deadlocks.
- **Idempotency Protection:** Client-supplied `Idempotency-Key` headers paired with SHA-256 canonical request fingerprints guarantee that network retries never produce duplicate charges.
- **Append-Only Immutability:** Historical ledger entries and transactions are strictly immutable (`@Immutable`, JPA `@PreUpdate`/`@PreRemove` lifecycle blocks, and repository delete blocks). Attempted modification or deletion of ledger records is strictly rejected.
- **Dual Transaction Boundary Auditing:** Completion audit events commit or roll back atomically with business mutations (`Propagation.REQUIRED`), while refusal and discrepancy audit events commit independently (`Propagation.REQUIRES_NEW`), guaranteeing non-repudiation without creating false audit records.

---

## 2. Technology Stack

### Backend
- **Language / Runtime:** Java 17 (Target) / JDK 24.0.2 (Host runtime)
- **Framework:** Spring Boot 4.1.2
- **Data Access:** Spring Data JPA / Hibernate (InnoDB)
- **Security:** Spring Security (Stateless JWT Authentication, BCrypt work factor 12, Role-Based Access Control)
- **Database Migrations:** Flyway
- **Relational Database:** MySQL 8.0 (MySQL 8.0.36 via Testcontainers)
- **Build Tool:** Apache Maven 3.10.0 (via Maven Wrapper)

### Frontend
- **Framework:** React 19.3.1
- **Language:** TypeScript ~6.0.3
- **Tooling / Bundler:** Vite 8.3.6
- **Styling:** Modern Responsive CSS (Dark Theme, High-Contrast Typography, Accessible Controls)
- **Architecture:** Component-driven Single-Page Application (SPA) with JWT Authentication and real REST API integration

---

## 3. Repository Directory Structure

```
LedgerLine/
├── .github/                           # CI/CD and modernization hooks
├── backend/
│   └── ledgerline-api/                # Spring Boot REST API
│       ├── .mvn/wrapper/              # Maven wrapper configuration
│       ├── Dockerfile                 # Multi-stage production container image
│       ├── src/
│       │   ├── main/
│       │   │   ├── java/com/ledgerline/
│       │   │   │   ├── config/        # Security, CORS & Web MVC configuration
│       │   │   │   ├── controller/    # REST endpoints (Auth, Account, Transfer, Auditor, Health)
│       │   │   │   ├── dto/           # Request / Response transfer objects
│       │   │   │   ├── entity/        # JPA Entities (Account, LedgerTransaction, LedgerEntry, AuditEvent, IdempotencyRecord)
│       │   │   │   ├── exception/     # RFC 7807 problem details handler
│       │   │   │   ├── repository/    # Spring Data JPA repositories with row locking
│       │   │   │   ├── security/      # JWT provider, filter, and user details
│       │   │   │   └── service/       # Business services (Transfer, Transaction, Reconciliation, Audit)
│       │   │   └── resources/
│       │   │       ├── application.properties      # Production TLS & externalized config
│       │   │       ├── application-dev.properties  # Local developer workstation defaults
│       │   │       └── db/migration/               # Flyway SQL migrations (V1, V2, V3)
│       │   └── test/
│       │       ├── java/com/ledgerline/            # 22 test classes (105 automated tests)
│       │       └── resources/
│       │           └── application.properties      # Test configuration
│       ├── mvnw
│       ├── mvnw.cmd
│       └── pom.xml
├── docs/                              # System Documentation
│   ├── PROJECT_AUDIT.md               # Day 1 technical audit of codebase
│   ├── ARCHITECTURE.md                # Layered architecture & sequence diagrams
│   ├── DATABASE_DESIGN.md             # Schemas for 8 tables & ER diagram
│   ├── FINANCIAL_INTEGRITY.md         # Concurrency, row locking & idempotency
│   ├── SECURITY_DESIGN.md             # Defense-in-depth security model
│   ├── REQUIREMENTS_MATRIX.md         # Requirements traceability matrix
│   └── API_DESIGN.md                  # REST API contract & error specifications
├── frontend/                          # React 19 + TypeScript + Vite SPA
│   ├── public/                        # Static web assets
│   ├── src/
│   │   ├── api.ts                     # REST client with JWT storage & idempotency headers
│   │   ├── types.ts                   # TypeScript interfaces matching backend DTOs
│   │   ├── App.css                    # Banking dashboard styling
│   │   ├── App.tsx                    # Main responsive dashboard & workflow components
│   │   ├── index.css                  # Global styles & design tokens
│   │   └── main.tsx                   # React root entrypoint
│   ├── .env.example                   # Environment variable template
│   ├── eslint.config.js
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
├── .gitignore                         # Comprehensive Git ignore rules
├── DEVELOPMENT_LOG.md                 # Daily engineering progress log
├── netlify.toml                       # Frontend Netlify deployment configuration
├── PROMPTS.md                         # Prompt logs & AI interaction history
├── README.md                          # Main project guide (this document)
├── render.yaml                        # Render Blueprint for backend API & MySQL
└── THREATS.md                         # Threat register with mitigations & tests
```

---

## 4. Completed Features & Verified Capabilities

### Double-Entry Financial Core
- **Balanced Journal Postings:** Every movement of funds is represented by at least one `DEBIT` and one `CREDIT` entry that sum to zero.
- **Clearing Accounts for External Movements:**
  - `POST /api/v1/accounts/{id}/deposits`: Debits system clearing account (`SYS_CLEARING_INR`) and credits customer account.
  - `POST /api/v1/accounts/{id}/withdrawals`: Debits customer account and credits system clearing account (`SYS_CLEARING_INR`).
- **Atomic Internal Transfers (`POST /api/v1/transfers`):** Debits sender account and credits recipient account within a single database transaction.
- **Deterministic Row Locking Order ($\min / \max$):** Accounts are always locked in ascending natural ID order ($\min(\text{src}, \text{dst})$ then $\max(\text{src}, \text{dst})$) via `SELECT ... FOR UPDATE`, mathematically eliminating circular-wait deadlocks during bidirectional concurrent transfers.
- **Fresh Read Principle:** Account locks are acquired as the first queries inside the transaction boundary, bypassing stale Hibernate L1 session caches.
- **Database-Enforced Idempotency:** Client `Idempotency-Key` headers paired with canonical SHA-256 digests serialize concurrent requests via `uk_idemp_user_op_key`. Exact retries return cached responses without moving money twice (`replayed: true`), while altered payloads on the same key are rejected with HTTP 409 Conflict.
- **Append-Only Immutability:** `LedgerEntry` and `LedgerTransaction` are protected by `@Immutable`, `@PreUpdate`/`@PreRemove` lifecycle callbacks, and repository-level deletion blocks.
- **Dual Transaction Boundary Auditing:** Completion events participate in caller transaction (`Propagation.REQUIRED`), while refusal logs commit autonomously (`Propagation.REQUIRES_NEW`).
- **Mathematical Reconciliation Engine:**
  - Account Reconciliation: Proves $\Delta = \text{Materialized Balance} - (\sum \text{Credits} - \sum \text{Debits}) == 0.00$.
  - System Reconciliation: Proves $\Delta = \sum \text{All Debits} - \sum \text{All Credits} == 0.00$ across all customer and clearing accounts.

### Banking Dashboard (Frontend)
- **Authentication:** Login and Registration with JWT token storage, authorization header injection, and quick-fill demo buttons.
- **Accounts Overview:** Real-time balances, account numbers, account type chips, status badges, and balance refresh button.
- **Transfer Interface:** Source account selection, recipient account input, amount presets (+₹100, +₹500, +₹1,000), interactive UUID `Idempotency-Key` generator, **"Replay Last Request"** button, and **"Test Conflict (409)"** button.
- **Deposit Interface:** Preset buttons (+₹500, +₹1,000, +₹5,000), description, double-entry clearing counterparty badge.
- **Withdrawal Interface:** Presets, overdraft check with RFC 7807 HTTP 422 error alerts.
- **Account Statement:** Chronological journal table displaying Date & Time, Entry Type (`DEBIT` in red, `CREDIT` in green), Amount, Balance After, Transaction Reference, and Description.
- **Live Math Audit Tab:** Real-time reconciliation panel displaying Materialized Balance, Computed Ledger Sum, and Discrepancy ($\Delta = ₹0.00$).
- **Auditor System Console:** Accessible for users with `ROLE_AUDITOR` or `ROLE_ADMIN`, demonstrating system-wide conservation status (`SYSTEM_BALANCED`), total debits, total credits, and zero unbalanced transactions.

---

## 5. Automated Verification & Test Results

The backend includes **105 automated tests** executing against real MySQL 8.0 via Testcontainers. Zero tests are mocked or skipped.

```
Test Class                                                Tests  Failures  Errors  Skipped
------------------------------------------------------------------------------------------
com.ledgerline.controller.AccountOwnershipTest             5         0       0        0
com.ledgerline.controller.AuthControllerTest               6         0       0        0
com.ledgerline.controller.DepositWithdrawalControllerTest  7         0       0        0
com.ledgerline.controller.HealthControllerTest             1         0       0        0
com.ledgerline.controller.TransferControllerTest           9         0       0        0
com.ledgerline.LedgerlineApiApplicationTests               1         0       0        0
com.ledgerline.repository.ConcurrentTransferTest           4         0       0        0
com.ledgerline.repository.ConcurrentWithdrawalTest         1         0       0        0
com.ledgerline.repository.MySqlAuthIntegrationTest         1         0       0        0
com.ledgerline.repository.MySqlLedgerImmutabilityTest      5         0       0        0
com.ledgerline.repository.MySqlLedgerIntegrationTest       2         0       0        0
com.ledgerline.repository.MySqlTransferIntegrationTest     3         0       0        0
com.ledgerline.security.AuthenticationManagerConfigTest    4         0       0        0
com.ledgerline.security.jwt.JwtTokenProviderTest           7         0       0        0
com.ledgerline.security.PasswordSecurityTest               3         0       0        0
com.ledgerline.security.RoleAuthorizationTest              5         0       0        0
com.ledgerline.service.AccountOwnershipServiceTest         7         0       0        0
com.ledgerline.service.AuditServiceTest                    3         0       0        0
com.ledgerline.service.DepositWithdrawalServiceTest        6         0       0        0
com.ledgerline.service.ReconciliationServiceTest           6         0       0        0
com.ledgerline.service.TransferIdempotencyTest             7         0       0        0
com.ledgerline.service.TransferServiceTest                12         0       0        0
------------------------------------------------------------------------------------------
Total                                                    105         0       0        0
```

### Key Concurrency & Integrity Test Scenarios
1. **Concurrent Overspending:** Two simultaneous ₹400 withdrawals against a ₹500 balance $\rightarrow$ exactly 1 succeeds, 1 fails with HTTP 422, final balance strictly ₹100.00.
2. **Opposite-Direction Transfers:** Simultaneous $A \rightarrow B$ and $B \rightarrow A$ transfers $\rightarrow$ deterministic $\min / \max$ locking prevents deadlocks.
3. **Concurrent Duplicate Keys:** 3 simultaneous threads submit the same `Idempotency-Key` $\rightarrow$ exactly 1 transfer executes, all 3 receive identical transaction references.
4. **Conflicting Payloads:** Same `Idempotency-Key` with altered amount $\rightarrow$ rejected with HTTP 409 Conflict.
5. **Ledger Immutability:** Attempted deletion or update of ledger entries or transactions throws `UnsupportedOperationException` and preserves database state.

---

## 6. How to Run Locally

### Prerequisites
- Java 17+ (JDK 24 compatible)
- Docker Desktop (running for Testcontainers and MySQL)
- Node.js 20+ and npm 10+

### Option A — Run Full Test Suite (Backend & Frontend)
```bash
# 1. Run all 105 backend tests against real MySQL Testcontainers:
& 'C:\Users\sanat\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd' test -f backend/ledgerline-api/pom.xml

# 2. Build the frontend production bundle:
npm run build --prefix frontend
```

### Option B — Run the Application Locally
```bash
# Terminal 1 — Start Backend API (Local Development Profile):
cd backend/ledgerline-api
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 2 — Start Frontend Dashboard:
cd frontend
npm run dev
```

Visit the dashboard in your browser: `http://localhost:5173`.

---

## 7. User Registration & Authentication

For security hygiene, the database starts with zero pre-seeded customer accounts (only the system clearing accounts `SYS_CLEARING_INR` and `SYS_CLEARING_USD` and core system roles exist by default).

To evaluate the application:
1. Navigate to the **Create Account** tab on the login screen.
2. Enter credentials or click **Fill Register: Alice** (e.g. username `demo_alice`, password `DemoPassword123!`, email `alice@example.com`).
3. Click **Create Account**. The backend securely hashes the password with BCrypt, assigns `ROLE_ACCOUNT_HOLDER`, and automatically provisions an initial active checking account.
4. Sign in to access the banking dashboard, deposit funds, execute transfers, inspect statements, and verify balance invariants.
5. Register a second account (e.g. `demo_bob`) to test peer-to-peer transfers and idempotency replay/conflict handling.

---

## 8. Deployment Architecture

LedgerLine is configured for zero-friction cloud deployment:

- **Backend:** Configured for Render via `render.yaml` and `backend/ledgerline-api/Dockerfile` with health check at `/health`.
- **Database:** Managed MySQL 8.0 on Render / Cloud SQL enforcing TLS transport encryption (`sslMode=REQUIRED`).
- **Frontend:** Configured for Netlify via `netlify.toml` with SPA client routing redirects (`/* -> /index.html`).
- **CORS:** Configured via `CORS_ALLOWED_ORIGINS` environment variable allowing production Netlify domains.

### Production Environment Variables

| Variable | Description | Example / Required Value |
| :--- | :--- | :--- |
| `DB_URL` | MySQL TLS connection URL | `jdbc:mysql://db.host:3306/ledgerline?sslMode=REQUIRED&serverTimezone=UTC` |
| `DB_USERNAME` | Production MySQL username | `ledgerline_prod` |
| `DB_PASSWORD` | Production MySQL password | `<secure_password>` |
| `JWT_SECRET` | HMAC-SHA256 signing secret (32+ bytes entropy) | `openssl rand -hex 32` |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend origins | `https://ledgerline.netlify.app,http://localhost:5173` |
| `VITE_API_URL` | Frontend API base URL | `https://ledgerline-api.onrender.com` |

---

## 9. Hackathon Roadmap & Completion Summary

| Phase | Description | Status |
| :---: | :--- | :---: |
| **Phase A** | Repository & Architecture Audit | **COMPLETED** |
| **Phase B** | Financial Core, Immutability & Concurrency Safety | **COMPLETED** |
| **Phase C** | React 19 + TypeScript Banking Dashboard | **COMPLETED** |
| **Phase D** | Test Suite Verification (105/105 Passed) | **COMPLETED** |
| **Phase E** | Deployment Configuration (Render, Netlify, Docker) | **COMPLETED** |
| **Phase F** | Documentation, Threat Register & Prompt Log | **COMPLETED** |
