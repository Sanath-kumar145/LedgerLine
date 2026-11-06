# LedgerLine — Money Movement With a Ledger You Can Prove

> **Snovasys Hackathon Project**
> **Status:** Day 1 Completed (Architecture, Database Design, Financial Integrity Specification & Baseline Build Verification)
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

## 4. Current State (End of Day 1)

As of **Day 1**, the repository has completed its architectural, security, database design, and build verification phase:
- **Project Audit Completed:** Discovered baseline issues (invalid wrapper URL, missing root `.gitignore`, test DataSource auto-configuration gap) and resolved them cleanly.
- **Architecture Formulated:** Multi-layered backend architecture and modular React frontend defined with Mermaid sequence flows.
- **Database Schema Designed:** Comprehensive designs for all 8 tables (`users`, `roles`, `user_roles`, `accounts`, `ledger_transactions`, `ledger_entries`, `idempotency_records`, `audit_events`).
- **Financial Concurrency Designed:** Deterministic locking, idempotency state machine, and overdraft prevention fully engineered.
- **Build Baselines Verified:**
  - Backend compile: `BUILD SUCCESS` (Java 17 target).
  - Backend tests: `BUILD SUCCESS` (Surefire 1 test run, 0 failures, 0 errors).
  - Frontend lint: `SUCCESS` (ESLint 0 errors, 0 warnings).
  - Frontend build: `SUCCESS` (Vite client bundle compiled in 415ms).

*Note: Business logic (authentication, transfers, deposits, withdrawals) is scheduled for subsequent days in accordance with the hackathon rules.*

---

## 5. Prerequisites & Local Setup

### Prerequisites
- **Java:** JDK 17 or higher (JDK 24 supported)
- **Node.js:** v20.x or v22.x
- **npm:** v10.x
- **MySQL:** MySQL Server 8.0+

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
| **Day 2** | **Authentication & Security** | User and Role entities, BCrypt hashing, Spring Security Filter Chain, JWT issuance, and Auth REST endpoints (`register`, `login`, `me`). | *Next* |
| **Day 3** | **Account Management & Funding** | Account entity, account creation, account lookups with IDOR protection, Flyway migrations, and initial deposit/withdrawal clearing. | Planned |
| **Day 4** | **Double-Entry Financial Core** | TransferService, deterministic row locking ($\min/\max$), double-entry postings, idempotency engine, and race-condition prevention. | Planned |
| **Day 5** | **Statements & Reconciliation** | Immutable statement queries, audit event capture for refused transactions, and auditor mathematical reconciliation engine. | Planned |
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
