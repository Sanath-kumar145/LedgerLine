# LedgerLine — Money Movement With a Ledger You Can Prove

> **Snovasys Hackathon Project**
> **Status:** Day 2 Completed (Authentication, Role-Based Access Control, Account Ownership Authorization, Testcontainers MySQL Integration Setup)
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

## 4. Current State (End of Day 2)

As of **Day 2**, the repository has completed authentication, role-based access control, account ownership authorization, and production test configuration:
- **Flyway Database Migration:** `V1__init_auth_schema.sql` establishes `roles`, `users`, `user_roles`, and `accounts` tables with balance constraints and seed data (`ROLE_ADMIN`, `ROLE_ACCOUNT_HOLDER`, `ROLE_AUDITOR`).
- **Domain Persistence Layer:** Implemented JPA entities (`User`, `Role`, `Account`), enums (`UserStatus`, `AccountType`, `AccountStatus`), and Spring Data repositories (`UserRepository`, `RoleRepository`, `AccountRepository`).
- **Security & Cryptography:** Configured Spring Security with stateless JWT validation (`JwtTokenProvider`, `JwtAuthenticationFilter`), BCrypt password hashing (work factor 12), and explicit `ProviderManager` wrapping `DaoAuthenticationProvider` and `CustomUserDetailsService`.
- **Role-Based Access Control:** Strict role segregation enforced across `/api/v1/auth/**` (public), `/api/v1/users/**` (authenticated), `/api/v1/admin/**` (ROLE_ADMIN only), and `/api/v1/accounts/**` (authenticated ownership).
- **IDOR / Ownership Protection:** `AccountServiceImpl` strictly enforces ownership checks (`account.getUserId().equals(currentUser.getId())`), allowing only account owners, administrators, or auditors to view account records. Cross-account access attempts emit security warnings and throw HTTP 403 Forbidden.
- **REST Endpoints Implemented:**
  - `POST /api/v1/auth/register` (returns 201 Created with clean profile response, zero credential exposure)
  - `POST /api/v1/auth/login` (authenticates credentials and returns signed JWT token)
  - `POST /api/v1/auth/logout` (stateless client cleanup confirmation)
  - `GET /api/v1/users/me` (returns authenticated user profile)
  - `GET /api/v1/admin/users` (administrative user directory, restricted to ROLE_ADMIN)
  - `GET /api/v1/accounts` (lists accounts owned by caller)
  - `GET /api/v1/accounts/{id}` (inspects account details with IDOR protection)
- **Standardized RFC 7807 Error Handling:** `GlobalExceptionHandler` returns structured ProblemDetail envelopes for validation errors, duplicate resources, 404 not found, and 403 forbidden without leaking stack traces.
- **Automated Verification:**
  - 34 automated unit, security, controller, and ownership tests pass cleanly (`BUILD SUCCESS`).
  - 2 MySQL integration tests (`LedgerlineApiApplicationTests`, `MySqlAuthIntegrationTest`) configured with Testcontainers MySQL 8.0 and gracefully skipped via `disabledWithoutDocker` when Docker daemon is offline.
  - Zero warnings for generated passwords or conflicting UserDetailsService beans.
  - Frontend production build (`npm run build`) verifies cleanly in 535ms.

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
| **Day 3** | **Account Management & Funding** | Foundation Flyway migrations for ledger tables, double-entry clearing accounts, deposit clearing, and withdrawal clearing with balance checks. | *Next* |
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
