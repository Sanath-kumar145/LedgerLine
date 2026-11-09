# LedgerLine — Money Movement With a Ledger You Can Prove

> **Snovasys Hackathon Project**
> **Status:** Day 4 Completed (Internal Transfers, Atomic Double-Entry Postings, Deterministic Account Locking Order, Database-Enforced Idempotency, Request Conflict Detection, Concurrency Safety & Deadlock Immunity, 94 Passing Tests Against Real MySQL 8.0 via Testcontainers)
> **Repository:** [https://github.com/Sanath-kumar145/LedgerLine.git](https://github.com/Sanath-kumar145/LedgerLine.git)

---

## 1. Project Overview

**LedgerLine** is an enterprise-grade financial ledger platform designed around a foundational principle: **Balances are never arbitrated by loose increments; they are governed by double-entry ledger journals with strict atomicity, row-level locking, and continuous reconciliation.**

Unlike typical applications that update balances via simple database increments (`UPDATE accounts SET balance = balance + ?`), LedgerLine records every movement of value as an immutable double-entry journal containing balanced debits and credits. Every account balance is directly provable and reconcilable against its underlying ledger history.

### Core Guarantees
- **Conservation of Money:** In every internal transaction, total debits equal total credits ($\sum \text{Debits} == \sum \text{Credits}$).
- **Overdraft Immunity:** Account balances are strictly prevented from dropping below zero via three independent defense rings: application validation, pessimistic locking with fresh reads, and database-level `CHECK (balance >= 0.00)` constraints.
- **Deadlock-Free Concurrency:** Bidirectional transfers between accounts acquire pessimistic row locks in strictly sorted natural ID order ($\min(\text{src}, \text{dst})$ then $\max(\text{src}, \text{dst})$), eliminating circular wait conditions.
- **Idempotency Protection:** Client-supplied `Idempotency-Key` headers paired with SHA-256 request fingerprints guarantee that network retries never produce duplicate charges.
- **Append-Only Truth:** Historical ledger entries and transactions are strictly immutable. Correction of errors requires compensating ledger entries, preserving an unalterable audit trail.

---

## 2. Technology Stack

### Backend
- **Language / Runtime:** Java 17 (Target) / JDK 24.0.2 (Host runtime)
- **Framework:** Spring Boot 4.1.2
- **Data Access:** Spring Data JPA / Hibernate (InnoDB)
- **Security:** Spring Security (Stateless JWT Authentication, BCrypt work factor 12, Role-Based Access Control)
- **Database Migrations:** Flyway
- **Relational Database:** MySQL 8.0
- **Build Tool:** Apache Maven 3.10.0 (via Maven Wrapper)

### Frontend
- **Framework:** React 19.3.1
- **Language:** TypeScript ~6.0.3
- **Tooling / Bundler:** Vite 8.3.6
- **Styling:** TailwindCSS
- **Routing:** React Router DOM
- **HTTP Client:** Axios with request/response interceptors
- **Icons:** Lucide React

---

## 3. Repository Directory Structure

```
LedgerLine/
├── .github/                           # CI/CD and modernization hooks
├── backend/
│   └── ledgerline-api/                # Spring Boot REST API
│       ├── .mvn/wrapper/              # Maven wrapper configuration
│       ├── src/
│       │   ├── main/
│       │   │   ├── java/com/ledgerline/
│       │   │   │   └── LedgerlineApiApplication.java
│       │   │   └── resources/
│       │   │       ├── application.properties
│       │   │       └── db/migration/  # Flyway SQL migrations
│       │   └── test/
│       │       ├── java/com/ledgerline/
│       │       │   └── LedgerlineApiApplicationTests.java
│       │       └── resources/
│       │           └── application.properties # Isolated test config
│       ├── mvnw
│       ├── mvnw.cmd
│       └── pom.xml
├── database/                          # Database initialization assets
├── docs/                              # System Documentation
│   ├── PROJECT_AUDIT.md               # Day 1 technical audit of codebase
│   ├── ARCHITECTURE.md                # Layered architecture & sequence diagrams
│   ├── DATABASE_DESIGN.md             # Schemas for 8 tables & ER diagram
│   ├── FINANCIAL_INTEGRITY.md         # Concurrency, row locking & idempotency
│   ├── SECURITY_DESIGN.md             # Defense-in-depth security model
│   ├── REQUIREMENTS_MATRIX.md         # Requirements traceability matrix
│   └── API_DESIGN.md                  # REST API contract & error specifications
├── frontend/                          # React + TypeScript + Vite SPA
│   ├── public/                        # Static web assets
│   ├── src/                           # UI source code
│   │   ├── assets/
│   │   ├── App.css
│   │   ├── App.tsx
│   │   ├── index.css
│   │   └── main.tsx
│   ├── eslint.config.js
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
├── .gitignore                         # Comprehensive Git ignore rules
├── DEVELOPMENT_LOG.md                 # Daily engineering progress log
├── PROMPTS.md                         # Prompt logs & AI interaction history
├── README.md                          # Main project guide (this document)
└── THREATS.md                         # Threat register with mitigations & tests
```

---

## 4. Current State (End of Day 4)

As of **Day 4**, the repository has implemented and verified internal account-to-account transfers, atomic double-entry postings, database-enforced idempotency, and concurrency safety:
- **Flyway Database Migrations:**
  - `V1__init_auth_schema.sql`: Core tables for `roles`, `users`, `user_roles`, and `accounts` with `CHECK (account_type = 'SYSTEM_CLEARING' OR balance >= 0.00)`.
  - `V2__init_ledger_schema.sql`: Double-entry core tables for `ledger_transactions`, `ledger_entries`, `audit_events`, auxiliary indexes, and seed clearing accounts (`SYS_CLEARING_INR`, `SYS_CLEARING_USD`).
  - `V3__init_idempotency_schema.sql`: Idempotency engine persistence table `idempotency_records` with unique constraint `(user_id, operation_type, idempotency_key)`, canonical request hash, and cached response payloads.
- **Domain Persistence Layer:**
  - Entities: `LedgerTransaction`, `LedgerEntry`, `AuditEvent`, `IdempotencyRecord`.
  - Repositories: `AccountRepository` with pessimistic write locks (`findByIdWithLock`, `findByAccountNumberWithLock`, `findByAccountTypeAndCurrencyWithLock`), `LedgerTransactionRepository`, `LedgerEntryRepository`, `AuditEventRepository`, `IdempotencyRecordRepository`.
- **Financial Architecture & Concurrency Guarantees:**
  - **Internal Account-to-Account Transfers:** Atomic transfer operation within a single database transaction. Debits source customer account and credits destination customer account with balanced double entries ($\sum \text{Debits} == \sum \text{Credits}$).
  - **Deterministic Account Lock Ordering ($\min / \max$):** Accounts are always locked in ascending natural ID order ($\min(\text{src}, \text{dst})$ then $\max(\text{src}, \text{dst})$) via `SELECT ... FOR UPDATE`, mathematically eliminating circular-wait deadlocks during bidirectional concurrent transfers.
  - **Fresh Read Principle:** Account locks are acquired as the first queries inside the transaction boundary, bypassing stale Hibernate L1 session caches.
  - **Database-Enforced Idempotency:** Client `Idempotency-Key` headers are verified against canonical SHA-256 request fingerprints. Exact retries return cached responses without moving money twice (`replayed: true`), while altered payloads on the same key are rejected with HTTP 409 Conflict.
  - **Autonomous Audit Logging (`Propagation.REQUIRES_NEW`):** Refused operations (e.g. `TRANSFER_REFUSED_INSUFFICIENT_FUNDS`) persist independently in `audit_events` even when the financial transaction rolls back.
- **REST Endpoints Implemented:**
  - `POST /api/v1/transfers` & `POST /api/transfers` (internal double-entry transfer with `Idempotency-Key` header)
  - `POST /api/v1/accounts/{id}/deposits` (authenticated deposit, double-entry clearing)
  - `POST /api/v1/accounts/{id}/withdrawals` (authenticated withdrawal, pessimistic lock, overdraft check)
  - `GET /api/v1/accounts/{id}/statement` (chronological statement of entries)
  - `GET /api/v1/accounts/{id}/reconciliation` (account-level math proof)
  - `GET /api/v1/auditor/reconcile/{accountId}` (ROLE_AUDITOR / ROLE_ADMIN account check)
  - `GET /api/v1/auditor/system-reconciliation` (ROLE_AUDITOR / ROLE_ADMIN system proof)
  - `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/users/me`, `GET /api/v1/admin/users`, `GET /api/v1/accounts`
- **Automated Verification:**
  - **94 automated tests executed and passing cleanly (0 failures, 0 errors, 0 skipped).**
  - **100% genuine MySQL 8.0 testing via Testcontainers** (Docker Engine 29.9.2 / Testcontainers 1.21.4).
  - Multi-threaded concurrency tests (`ConcurrentTransferTest` & `ConcurrentWithdrawalTest`):
    - Concurrent overspending prevention (₹500 balance, two ₹400 transfers $\rightarrow$ exactly 1 succeeds, 1 fails with HTTP 422).
    - Opposite-direction transfers ($A \rightarrow B$ and $B \rightarrow A$ concurrently $\rightarrow$ zero deadlocks).
    - Concurrent duplicate keys (multiple concurrent threads with same key $\rightarrow$ exactly 1 transfer executed, all threads resolve to same result).
    - Conflicting payload detection (concurrent same key with different amounts $\rightarrow$ exactly 1 accepted, 1 rejected with 409 Conflict).
  - Frontend production build (`npm run build`) verified cleanly in 510ms.

---

## 5. Prerequisites & Local Setup

### Prerequisites
- **Java:** JDK 17 or higher (JDK 24 supported)
- **Node.js:** v20.x or v22.x
- **npm:** v10.x
- **MySQL:** MySQL Server 8.0+ or Docker Desktop (for Testcontainers)

### Environment Variables & Production Security
For production deployment, configure the following mandatory environment variables:
- `DB_URL`: MySQL connection string enforcing TLS transport encryption (e.g. `jdbc:mysql://db.production:3306/ledgerline?sslMode=REQUIRED&serverTimezone=UTC`). Insecure flags (`useSSL=false`, `allowPublicKeyRetrieval=true`) are strictly forbidden in production.
- `DB_USERNAME`: Database username (externalized, no hardcoded defaults).
- `DB_PASSWORD`: Database password (externalized, no hardcoded defaults).
- `JWT_SECRET`: Cryptographically secure HMAC-SHA256 signing secret containing **at least 32 bytes (256 bits) of entropy**. Short or empty secrets will fail application startup with an `IllegalArgumentException`.
- `JWT_EXPIRATION_MS`: Token lifetime in milliseconds (default: `86400000` / 24 hours).

#### Generating a Cryptographically Secure JWT_SECRET
To generate a secure 256-bit (32-byte) secret, run one of the following commands:
```bash
# Generate 64-character hexadecimal secret (32 bytes entropy):
openssl rand -hex 32

# Or generate 44-character Base64-encoded secret (32 bytes entropy):
openssl rand -base64 32
```

#### Local Development Profile
For local developer workstations without TLS certificates configured on local MySQL, activate the separate `dev` profile:
```bash
# Run with local development profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```
This isolates local developer convenience defaults (`application-dev.properties`) from strict production configurations (`application.properties`).

### Backend Setup
1. Open a terminal in `backend/ledgerline-api`:
   ```bash
   cd backend/ledgerline-api
   ```
2. Build the project:
   ```bash
   ./mvnw compile
   ```
3. Run the automated tests:
   ```bash
   ./mvnw test
   ```

### Frontend Setup
1. Open a terminal in `frontend`:
   ```bash
   cd frontend
   ```
2. Install dependencies (if not already installed):
   ```bash
   npm install
   ```
3. Run the development server:
   ```bash
   npm run dev
   ```
4. Run lint checks and build:
   ```bash
   npm run lint
   npm run build
   ```

---

## 6. Hackathon Roadmap

| Development Day | Milestone | Scope | Status |
| :---: | :--- | :--- | :---: |
| **Day 1** | **Audit, Architecture & Design** | Repository audit, system architecture, database schema, concurrency design, threat model, API specifications, and baseline builds. | **COMPLETED** |
| **Day 2** | **Authentication, RBAC & Ownership** | User/Role entities, BCrypt work factor 12, stateless JWT, RBAC guards, account ownership IDOR defense, RFC 7807 handler, and Testcontainers setup. | **COMPLETED** |
| **Day 3** | **Account Management & Funding** | Foundation Flyway migrations for ledger tables, double-entry clearing accounts, deposit clearing, and withdrawal clearing with balance checks. | **COMPLETED** |
| **Day 4** | **Double-Entry Financial Core** | TransferService, deterministic row locking ($\min/\max$), double-entry postings, idempotency engine, and race-condition prevention. | **COMPLETED** |
| **Day 5** | **Statements & Reconciliation** | Immutable statement queries, audit event capture for refused transactions, and auditor mathematical reconciliation engine. | *Next* |
| **Day 6** | **Frontend Application** | React 19 UI, authentication flows, dashboard, transfer interface with proof badges, statement view, and auditor console. | Planned |
| **Day 7** | **Integration & Concurrency Testing** | Multi-threaded race condition tests (CountDownLatch), stress testing, edge-case hardening, and final delivery polish. | Planned |

---

## 7. Documentation Directory Quick Links

- [Repository Audit Report](docs/PROJECT_AUDIT.md)
- [System Architecture Specification](docs/ARCHITECTURE.md)
- [Relational Database Design](docs/DATABASE_DESIGN.md)
- [Financial Integrity & Concurrency Architecture](docs/FINANCIAL_INTEGRITY.md)
- [Security Architecture & Design](docs/SECURITY_DESIGN.md)
- [Threat Register](THREATS.md)
- [Requirements Traceability Matrix](docs/REQUIREMENTS_MATRIX.md)
- [RESTful API Specification](docs/API_DESIGN.md)
- [Development Log](DEVELOPMENT_LOG.md)
- [Prompts Record](PROMPTS.md)
