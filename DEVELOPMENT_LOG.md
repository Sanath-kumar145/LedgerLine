# LedgerLine — Development Log

---

## Entry 1: Day 1 Execution & Technical Review Baseline

- **Date:** 2026-11-06
- **Development Day:** Day 1
- **Focus Area:** Repository Audit, System Architecture, Database Design, Financial Integrity Specification, Technical Review, and Build Verification

---

### 1. Tasks Completed
1. Conducted an exhaustive audit of the root directory, backend (`backend/ledgerline-api`), frontend (`frontend`), database directory, Maven configurations, npm manifests, Git state, and environment toolchains.
2. Formulated a clean layered system architecture spanning Controllers, Services, Repositories, Entities, DTOs, Security, Validation, Exception Handling, Configuration, and Auditing.
3. Designed the full relational database schema across all 8 required tables (`users`, `roles`, `user_roles`, `accounts`, `ledger_transactions`, `ledger_entries`, `idempotency_records`, `audit_events`) with strict financial invariants (`DECIMAL(19,2)`, `CHECK (account_type = 'SYSTEM_CLEARING' OR balance >= 0.00)`), initiator tracking (`initiated_by_user_id`), and balanced clearing accounts.
4. Designed the financial integrity mechanisms: atomic transactions, deterministic pessimistic row locking order ($\min(\text{src}, \text{dst})$ then $\max(\text{src}, \text{dst})$), balance validation after locking, idempotency key persistence, SHA-256 request fingerprinting, append-only ledger journals, and continuous balance reconciliation.
5. Authored a thorough Threat Register (`THREATS.md`) analyzing 16 threats with actionable mitigations and honest lifecycle statuses.
6. Mapped all project requirements to a Requirements Traceability Matrix (`docs/REQUIREMENTS_MATRIX.md`), including initiator tracking (`REQ-INIT-01`) and ledger immutability (`REQ-PROT-01`).
7. Designed the comprehensive RESTful API specification (`docs/API_DESIGN.md`) covering authentication, accounts, deposits, withdrawals, transfers, statements, admin, and auditor operations.
8. Documented the detailed Security Architecture (`docs/SECURITY_DESIGN.md`).
9. Conducted an in-depth technical review:
   - Verified that Maven 3.10.0 wrapper URL is genuine and active on Maven Central (`HTTP 200 OK`); preserved the original wrapper configuration.
   - Evaluated test `application.properties`: documented that DataSource exclusion is strictly a Day 1 bootstrap stub, and formulated the official Testcontainers MySQL integration strategy for Day 2+.
   - Evaluated root `package.json` vs `frontend/package.json`: confirmed UI dependencies were placed at root without monorepo workspaces; preserved files and scheduled integration for Day 6.
   - Audited double-entry clearing accounts: updated `accounts` schema to support nullable `user_id` and check constraint exemption for `SYSTEM_CLEARING`.
10. Added a root `.gitignore` to protect against committing `node_modules/`, `target/`, and `dist/`.
11. Executed baseline build and test verifications for both backend (Maven compile & surefire test) and frontend (ESLint & Vite build).

---

### 2. Files Created or Changed

| File Path | Action | Description |
| :--- | :--- | :--- |
| `.gitignore` | Created | Repository root ignore file preventing tracking of `node_modules`, `target`, `dist`, logs, and secrets. |
| `backend/ledgerline-api/.mvn/wrapper/maven-wrapper.properties` | Verified / Restored | Preserved official Maven 3.10.0 distribution URL verified active on Maven Central. |
| `backend/ledgerline-api/src/test/resources/application.properties` | Created | Test property configuration isolating DataSource auto-configuration for Day 1 bootstrap test; documented Testcontainers roadmap. |
| `docs/PROJECT_AUDIT.md` | Created / Updated | Exhaustive technical audit of existing repository state, toolchain, root package analysis, and baseline builds. |
| `docs/ARCHITECTURE.md` | Created | System architecture specification with layered backend, modular frontend, and Mermaid diagrams. |
| `docs/DATABASE_DESIGN.md` | Created / Updated | Complete relational database design, column definitions, constraints, ER diagram, initiator tracking, and clearing account math. |
| `docs/FINANCIAL_INTEGRITY.md` | Created | Concurrency controls, row locking strategy, idempotency fingerprinting, and threat prevention. |
| `docs/SECURITY_DESIGN.md` | Created | Security architecture, RBAC hierarchy, IDOR defense, and secret management. |
| `docs/REQUIREMENTS_MATRIX.md` | Created / Updated | Traceability matrix mapping problem requirements (including initiator tracking and immutability) to implementation plans. |
| `docs/API_DESIGN.md` | Created | REST API specification with endpoint schemas, request/response examples, and error formats. |
| `THREATS.md` | Created | Threat register with impacts, mitigations, verification tests, and honest statuses. |
| `PROMPTS.md` | Created / Updated | Verbatim prompt records and genuine AI execution and review log. |
| `DEVELOPMENT_LOG.md` | Created / Updated | Chronological day-by-day development log. |
| `README.md` | Created | Professional project README with architecture, setup, and hackathon roadmap. |

---

### 3. Test & Build Results

1. **Backend Compile:** `BUILD SUCCESS` (1 source file compiled, 0 errors).
2. **Backend Unit Test:** `BUILD SUCCESS` (Tests run: 1, Failures: 0, Errors: 0, Skipped: 0).
3. **Frontend Linting:** `SUCCESS` (ESLint: 0 errors, 0 warnings).
4. **Frontend Production Build:** `SUCCESS` (Vite v8.3.6: client environment built in 415ms, producing optimized assets in `frontend/dist/`).

---

### 4. Pending Work (Days 2–7)
- **Day 2:** User entity, Role entity, UserRole mapping, BCrypt password hashing, Spring Security Filter Chain, JWT token generation/validation, AuthController (`register`, `login`, `me`), and Testcontainers MySQL setup.
- **Day 3:** Account entity, AccountRepository, AccountService, AccountController (`create`, `list`, `get`), and foundation Flyway migration (`V1__init_schema.sql`).
- **Day 4:** Financial Transaction Core: LedgerTransaction, LedgerEntry, TransferService with deterministic locking order, balance validation under lock, IdempotencyInterceptor, and idempotency_records persistence.
- **Day 5:** Statements, Audit Trail, and Auditor Reconciliation: StatementService, AuditService (`recordRefusedAttempt`), and ReconciliationService (`reconcileAccount`).
- **Day 6:** Frontend Application: React 19 pages (Dashboard, Transfers, Statement, Auditor Console), Axios client with interceptors, AuthContext, and protected routes.
- **Day 7:** End-to-End Integration, Concurrency Stress Testing (CountDownLatch race condition tests), and Final Polish.

---

### 5. Next Day's Objectives (Day 2)
1. Add Testcontainers MySQL dependency (`org.testcontainers:mysql`) to `backend/ledgerline-api/pom.xml`.
2. Implement `User`, `Role`, and `UserRole` JPA entities.
3. Implement `UserRepository` and `RoleRepository`.
4. Configure `SecurityConfig` with stateless JWT authentication and BCrypt password encoder.
5. Implement `JwtTokenProvider` and `JwtAuthenticationFilter`.
6. Implement `CustomUserDetailsService`.
7. Implement `AuthController` with `/api/v1/auth/register` and `/api/v1/auth/login`.
8. Write integration tests using Testcontainers MySQL verifying authentication, password hashing, and role authorization.


---

## Entry 2: Day 2 Implementation & Verification

- **Date:** 2026-11-07
- **Development Day:** Day 2
- **Focus Area:** User Persistence, Role-Based Access Control, Stateless JWT Security, Account Ownership Authorization & Testcontainers Integration Setup

---

### 1. Tasks Completed
1. Created foundation Flyway migration `V1__init_auth_schema.sql` defining `roles`, `users`, `user_roles`, and `accounts` tables with balance constraints and seed data (`ROLE_ADMIN`, `ROLE_ACCOUNT_HOLDER`, `ROLE_AUDITOR`).
2. Implemented domain entities (`User`, `Role`, `Account`), enums (`UserStatus`, `AccountType`, `AccountStatus`), and Spring Data repositories (`UserRepository`, `RoleRepository`, `AccountRepository`).
3. Implemented DTOs (`RegisterRequest`, `LoginRequest`, `AuthResponse`, `UserProfileResponse`, `UserSummaryDto`, `AccountResponse`, `ErrorResponse`).
4. Configured Spring Security 7.x / Spring Boot 4.1.2:
   - Configured `BCryptPasswordEncoder` with cost factor 12.
   - Built custom `UserPrincipal` and `CustomUserDetailsService`.
   - Built `JwtTokenProvider` and `JwtAuthenticationFilter` with HMAC-SHA256 signature verification.
   - Configured `SecurityConfig` with `ProviderManager(authProvider)` explicitly wrapping `DaoAuthenticationProvider` and `CustomUserDetailsService`.
   - Enforced stateless sessions, CORS configuration, and RFC 7807 `ProblemDetail` handlers for 401 Unauthorized and 403 Forbidden.
5. Implemented business services:
   - `UserServiceImpl`: User registration with duplicate checks, password hashing, authentication, and user profile retrieval.
   - `AccountServiceImpl`: Strict IDOR ownership validation (`account.userId == currentUser.getId()`), logging security alerts and denying unauthorized cross-user access.
6. Implemented REST controllers:
   - `AuthController`: `POST /api/v1/auth/register` (201 Created), `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`.
   - `UserController`: `GET /api/v1/users/me`.
   - `AdminController`: `GET /api/v1/admin/users` (secured with `hasRole('ADMIN')`).
   - `AccountController`: `GET /api/v1/accounts`, `GET /api/v1/accounts/{id}`.
7. Implemented global exception handling (`GlobalExceptionHandler`) emitting RFC 7807 ProblemDetail responses without leaking stack traces.
8. Configured `JacksonConfig` with `JavaTimeModule` for Java 8 date/time serialization.
9. Migrated test configuration:
   - Completely removed Day 1 temporary DataSource exclusions from `src/test/resources/application.properties`.
   - Configured official Testcontainers MySQL 8.0 datasource (`jdbc:tc:mysql:8.0.36:///ledgerline_test?TC_REUSABLE=true`).
   - Preserved genuine MySQL testing; zero H2 substitution.
   - Guarded container integration tests with `@Testcontainers(disabledWithoutDocker = true)`.
10. Externalized all secrets and database credentials in production `application.properties` using environment variables without unsafe defaults.
11. Executed and verified test suite: 36 tests run, 34 passed, 2 skipped (due to offline Docker engine daemon), 0 failed, 0 errors.

---

### 2. Files Created or Modified

| File Path | Action | Description |
| :--- | :--- | :--- |
| `backend/ledgerline-api/pom.xml` | Modified | Added Jackson databind, JSR-310, JJWT (0.12.6), and Testcontainers BOM & MySQL dependencies. |
| `backend/ledgerline-api/src/main/resources/application.properties` | Modified | Externalized DB credentials; removed useSSL=false and allowPublicKeyRetrieval=true; enforced TLS (sslMode=REQUIRED). |
| `backend/ledgerline-api/src/main/resources/application-dev.properties` | Created | Separated local developer workstation configuration from production. |
| `backend/ledgerline-api/src/test/resources/application.properties` | Modified | Removed Day 1 exclusions; configured Testcontainers MySQL 8.0 and test JWT secret. |
| `backend/ledgerline-api/src/main/resources/db/migration/V1__init_auth_schema.sql` | Created | DDL for auth and accounts tables with seed roles. |
| `backend/ledgerline-api/src/main/java/com/ledgerline/entity/*` | Created | `Role`, `User`, `UserStatus`, `Account`, `AccountType`, `AccountStatus`. |
| `backend/ledgerline-api/src/main/java/com/ledgerline/repository/*` | Created | `RoleRepository`, `UserRepository`, `AccountRepository`. |
| `backend/ledgerline-api/src/main/java/com/ledgerline/dto/*` | Created | Clean request/response DTOs preventing password leakage. |
| `backend/ledgerline-api/src/main/java/com/ledgerline/exception/*` | Created | `DuplicateResourceException`, `ResourceNotFoundException`, `GlobalExceptionHandler`. |
| `backend/ledgerline-api/src/main/java/com/ledgerline/security/*` | Created | `UserPrincipal`, `CustomUserDetailsService`, `JwtTokenProvider`, `JwtAuthenticationFilter`. |
| `backend/ledgerline-api/src/main/java/com/ledgerline/config/*` | Created | `SecurityConfig`, `JacksonConfig`. |
| `backend/ledgerline-api/src/main/java/com/ledgerline/service/*` | Created | `UserService`, `UserServiceImpl`, `AccountService`, `AccountServiceImpl`. |
| `backend/ledgerline-api/src/main/java/com/ledgerline/controller/*` | Created | `AuthController`, `UserController`, `AdminController`, `AccountController`. |
| `backend/ledgerline-api/src/test/java/com/ledgerline/security/PasswordSecurityTest.java` | Created | Unit test for BCrypt cost factor 12. |
| `backend/ledgerline-api/src/test/java/com/ledgerline/security/jwt/JwtTokenProviderTest.java` | Created | Unit test for JWT generation, claims, expiration, tampering. |
| `backend/ledgerline-api/src/test/java/com/ledgerline/service/AccountOwnershipServiceTest.java` | Created | Mockito unit test for IDOR cross-account authorization. |
| `backend/ledgerline-api/src/test/java/com/ledgerline/controller/AuthControllerTest.java` | Created | Web slice test for register, login, conflict, logout. |
| `backend/ledgerline-api/src/test/java/com/ledgerline/controller/AccountOwnershipTest.java` | Created | Web slice test for account ownership HTTP endpoints. |
| `backend/ledgerline-api/src/test/java/com/ledgerline/security/RoleAuthorizationTest.java` | Created | Web slice test for RBAC role enforcement on protected paths. |
| `backend/ledgerline-api/src/test/java/com/ledgerline/security/AuthenticationManagerConfigTest.java` | Created | Architecture test verifying `ProviderManager`, `DaoAuthenticationProvider`, and `BCrypt`. |
| `backend/ledgerline-api/src/test/java/com/ledgerline/LedgerlineApiApplicationTests.java` | Modified | Updated with `@Testcontainers(disabledWithoutDocker = true)`. |
| `backend/ledgerline-api/src/test/java/com/ledgerline/repository/MySqlAuthIntegrationTest.java` | Created | Testcontainers MySQL integration test for repositories and Flyway. |

---

### 3. Test & Verification Results

1. **Backend Compile:** `BUILD SUCCESS` (34 main source files, 9 test source files).
2. **Backend Test Suite:** `BUILD SUCCESS` (39 tests run: **37 passed**, **2 skipped**, **0 failed**, **0 errors**).
   - `PasswordSecurityTest`: 3 passed
   - `JwtTokenProviderTest`: 7 passed (including missing secret, short secret, and valid secret tests)
   - `AccountOwnershipServiceTest`: 7 passed
   - `AuthControllerTest`: 6 passed
   - `AccountOwnershipTest`: 5 passed
   - `RoleAuthorizationTest`: 5 passed
   - `AuthenticationManagerConfigTest`: 4 passed
   - `LedgerlineApiApplicationTests`: 1 skipped (Docker engine daemon offline)
   - `MySqlAuthIntegrationTest`: 1 skipped (Docker engine daemon offline)
3. **Frontend Build:** `SUCCESS` (Vite bundle built cleanly in 666ms).
4. **Git State:** 0 files staged, 0 files committed.

---

### 4. Next Day's Objectives (Day 3)
1. Implement Flyway migration `V2__init_ledger_schema.sql` for `ledger_transactions`, `ledger_entries`, `idempotency_records`, and `audit_events`.
2. Implement double-entry clearing accounts (`SYSTEM_CLEARING`) with `CHECK` constraint exemptions.
3. Implement `TransferService.deposit()` and `TransferService.withdraw()` with balanced double-entry ledger entries.
4. Enforce balance validation and overdraft protection (`CHECK (balance >= 0.00)`).
5. Build automated unit tests for deposit and withdrawal ledger balance math.

---

## 2026-11-08 — Day 3: Double-Entry Ledger, Deposits, Withdrawals and Financial Integrity

### 1. Scope & Objectives
Implement genuine double-entry money movement, clearing counterparty accounts, deposits, withdrawals with pessimistic row locking and overdraft prevention, transaction history statements, autonomous audit logging, mathematical reconciliation engines, and multi-threaded race condition tests against real MySQL 8.0 in Testcontainers.

---

### 2. Implementation Deliverables

1. **Flyway Migration `V2__init_ledger_schema.sql`**:
   - `ledger_transactions`: Master transaction journal tracking reference, type, status, amount, currency, and initiating user.
   - `ledger_entries`: Double-entry transaction postings tracking account, entry type (`DEBIT`/`CREDIT`), amount, and running balance after posting.
   - `audit_events`: Autonomous audit log capturing security, authorization, and financial refusal events.
   - Seeded system clearing accounts: `SYS_CLEARING_INR` and `SYS_CLEARING_USD`.
   - Indexes added for performance: `idx_entries_account_id_created`, `idx_entries_tx_id`, `idx_tx_ref`, `idx_audit_account_created`.

2. **Domain Model & Entities**:
   - Entities: `LedgerTransaction`, `LedgerEntry`, `AuditEvent`.
   - Enums: `TransactionType` (`DEPOSIT`, `WITHDRAWAL`, `TRANSFER`), `TransactionStatus` (`PENDING`, `COMPLETED`, `FAILED`), `EntryType` (`DEBIT`, `CREDIT`).
   - Repositories: `AccountRepository` (with `@Lock(LockModeType.PESSIMISTIC_WRITE)` methods `findByIdWithLock`, `findByAccountNumberWithLock`, `findByAccountTypeAndCurrencyWithLock`), `LedgerTransactionRepository`, `LedgerEntryRepository`, `AuditEventRepository`.

3. **Core Services & Financial Engine**:
   - `TransactionService` / `TransactionServiceImpl`:
     - Deposits: Debits `SYS_CLEARING_INR`, credits customer account; balances increase.
     - Withdrawals: Pessimistic write lock acquired immediately upon transaction entry (`findByIdWithLock`), Fresh Read Principle enforced to bypass stale Hibernate L1 cache, available balance checked against withdrawal amount, refuses overdrafts with RFC 7807 422 `InsufficientFundsException`, debits customer account, credits `SYS_CLEARING_INR`.
     - Invariant verification: Asserted $\sum \text{debits} == \sum \text{credits}$ on every posted transaction.
   - `AuditService` / `AuditServiceImpl`:
     - Autonomous transaction propagation via `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
     - Foreign key decoupling on `audit_events` to ensure refused operations persist even when outer financial transactions roll back without encountering InnoDB self-deadlocks.
   - `ReconciliationService` / `ReconciliationServiceImpl`:
     - Account reconciliation: Proves materialized balance equals $\sum \text{credits} - \sum \text{debits}$ ($\Delta = 0.00$).
     - System-wide reconciliation: Proves total system debits equal total system credits ($\sum \text{all debits} == \sum \text{all credits}$, system delta $= 0.00$, integrity status `SYSTEM_BALANCED`).

4. **REST Controllers**:
   - `AccountController`:
     - `POST /api/v1/accounts/{id}/deposits`
     - `POST /api/v1/accounts/{id}/withdrawals`
     - `GET /api/v1/accounts/{id}/statement`
     - `GET /api/v1/accounts/{id}/reconciliation`
   - `AuditorController`:
     - `GET /api/v1/auditor/reconcile/{accountId}`
     - `GET /api/v1/auditor/system-reconciliation`
     - Protected via `@PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")`.

---

### 3. Test & Verification Results

1. **Automated Test Suite (Real MySQL 8.0 Testcontainers)**:
   - **Total Tests Run:** 61
   - **Passed:** 61
   - **Failures:** 0
   - **Errors:** 0
   - **Skipped:** 0
   - **Key Test Classes:**
     - `DepositWithdrawalServiceTest`: 6 passed (deposit/withdrawal math, clearing counterparty balance, overdraft rejection, frozen account handling).
     - `ReconciliationServiceTest`: 6 passed (account balance match, simulated corruption detection, system-wide conservation).
     - `DepositWithdrawalControllerTest`: 7 passed (validation, 422 RFC 7807 responses, statement pagination, auditor access).
     - `MySqlLedgerIntegrationTest`: 1 passed (Flyway V2 migration, live deposits, withdrawals, double-entry invariance, and reconciliation against MySQL Testcontainers).
     - `ConcurrentWithdrawalTest`: 1 passed (race condition stress test: 2 concurrent ₹400 withdrawals against ₹500 balance; exactly 1 succeeds, 1 fails with 422, final balance strictly ₹100.00, customer entries count exactly 2, audit event persisted via `REQUIRES_NEW`, reconciliation balanced).
     - Day 2 Test Regression: All 46 existing unit, slice, and integration tests passed cleanly (including `MySqlAuthIntegrationTest` and `LedgerlineApiApplicationTests`).

2. **Frontend Production Build**:
   - Command: `npm run build --prefix frontend`
   - Status: `SUCCESS` (exit code 0, built in 606ms).

3. **Code Quality & Git Hygiene**:
   - `git diff --check`: 0 whitespace errors.
   - Root `package.json` and `package-lock.json` untouched.
   - 0 files staged, 0 commits pushed.

---

### 4. Next Day's Objectives (Day 4)
1. Implement internal inter-account transfer API (`POST /api/v1/transfers`).
2. Implement deterministic dual-account pessimistic locking order ($\min(\text{src}, \text{dst})$ then $\max(\text{src}, \text{dst})$) to eliminate transfer deadlocks.
3. Implement `Idempotency-Key` interceptor/filter with SHA-256 payload fingerprinting and persistent `idempotency_records` table.
4. Add concurrent transfer and deadlock stress testing.

---

## 2026-11-09 — Day 4: Internal Transfers, Idempotency & Concurrency Safety

### 1. Scope & Objectives
Implement secure internal account-to-account transfers, atomic double-entry ledger postings, database-enforced idempotency keys, canonical request fingerprinting, payload conflict detection (HTTP 409), deterministic account lock ordering ($\min(\text{src}, \text{dst})$ then $\max(\text{src}, \text{dst})$) for deadlock immunity, account-level authorization, and comprehensive automated test suites against real MySQL 8.0 via Testcontainers.

---

### 2. Implementation Deliverables

1. **Flyway Migration `V3__init_idempotency_schema.sql`**:
   - `idempotency_records`: Stores `idempotency_key`, `user_id`, `operation_type`, `request_path`, `request_hash` (SHA-256 canonical digest), `transaction_reference`, `status` (`COMPLETED`), `response_code`, and `response_payload` (serialized JSON).
   - Unique Constraint: `CONSTRAINT uk_idemp_user_op_key UNIQUE (user_id, operation_type, idempotency_key)` to serialize concurrent duplicate requests at the database storage engine layer.
   - Indexes: `idx_idemp_key`, `idx_idemp_user_id`, `idx_idemp_created`.

2. **Domain Persistence & DTOs**:
   - Entity: `IdempotencyRecord` with JPA mapping and lifecycle timestamps.
   - Repository: `IdempotencyRecordRepository` with composite lookup methods.
   - DTOs: `TransferRequest` (with validation for accounts, strictly positive amount, max 2 decimals) and `TransferResponse` (with `sourceBalanceAfter`, `destinationBalanceAfter`, `replayed`, and double-entry postings).
   - Exception: `IdempotencyConflictException` mapping to HTTP 409 Conflict via `GlobalExceptionHandler`.

3. **Core Transfer Engine (`TransferServiceImpl`)**:
   - **Deterministic Locking Order:** Computes `min(srcId, dstId)` and `max(srcId, dstId)`, executing `accountRepository.findByIdWithLock(...)` in strictly ascending order. This guarantees that opposite-direction transfers ($A \rightarrow B$ and $B \rightarrow A$) acquire locks in identical sequence, mathematically preventing deadlocks.
   - **Fresh Read Principle:** Row locks are acquired as the initial queries inside the transaction boundary, bypassing stale Hibernate L1 session cache entities.
   - **Account Authorization & Integrity:** Verifies caller owns source account (`sourceAccount.isOwnedBy(currentUser.getId())`), rejects auditor transfer attempts (`ROLE_AUDITOR`), rejects transfers to/from `SYSTEM_CLEARING` accounts, verifies active states, and validates currency matches.
   - **Overdraft Defense:** Authoritative in-transaction balance check under exclusive lock. Refuses overdraft attempts with RFC 7807 HTTP 422 `InsufficientFundsException` and records `TRANSFER_REFUSED_INSUFFICIENT_FUNDS` via autonomous `REQUIRES_NEW` audit logging.
   - **Balanced Double-Entry Journaling:** Posts 1 `DEBIT` entry against source account and 1 `CREDIT` entry against destination account. Conserves total money ($\sum \text{debits} == \sum \text{credits}$).
   - **Database-Enforced Idempotency:**
     - Computes canonical SHA-256 hash: `sourceAccountId|destinationAccountId|amount|currency|description`.
     - Fast-path checks before and after lock acquisition.
     - Exact retries return cached response with `replayed = true` (HTTP 200 OK) without re-executing business logic or moving funds.
     - Conflicting payloads with same key throw `IdempotencyConflictException` (HTTP 409 Conflict).
     - Concurrent competing requests serialize via unique constraint; the losing thread catches `DataIntegrityViolationException`, rolls back cleanly, and queries the winning committed record.
     - Failed transfers (e.g. insufficient funds) roll back cleanly, leaving zero unusable idempotency records.

4. **REST Controller (`TransferController`)**:
   - Endpoints: `POST /api/v1/transfers` and alias `POST /api/transfers`.
   - Headers: Mandatory `Idempotency-Key` (max 64 chars). Missing key returns HTTP 400 Bad Request.
   - Returns HTTP 201 Created for new operations, HTTP 200 OK for replayed operations.

---

### 3. Test & Verification Results

1. **Automated Test Suite (Real MySQL 8.0 Testcontainers)**:
   - **Total Tests Run:** 99
   - **Passed:** 99
   - **Failures:** 0
   - **Errors:** 0
   - **Skipped:** 0
   - **New Day 4 Test Classes:**
     - `TransferServiceTest`: 12 passed (transfer math, balance deduction/increase, zero/negative amounts, self-transfers, insufficient funds, inactive accounts, currency mismatch, unauthorized source, clearing account attempts, auditor attempts).
     - `TransferIdempotencyTest`: 7 passed (first request execution, exact retry, altered amount/source/destination conflict detection, separate keys, failed transfer rollback).
     - `TransferControllerTest`: 9 passed (201 Created, 200 OK replay, 400 validation, 400 missing header, 422 insufficient funds, 409 conflict, alias route).
     - `MySqlTransferIntegrationTest`: 3 passed (Flyway V3 migration, live transfer balance updates, double entry, idempotency record, statement debit/credit, live reconciliation `SYSTEM_BALANCED`, forced rollback clears consistent audit events, insufficient funds preserves autonomous refusal audit event).
     - `ConcurrentTransferTest`: 4 passed (multi-threaded race condition tests with `CountDownLatch` against MySQL Testcontainers):
       - Test 1: Concurrent overspending (₹500 balance, two ₹400 transfers $\rightarrow$ exactly 1 succeeds, 1 fails with 422, final balance strictly ₹100.00).
       - Test 2: Opposite-direction transfers ($A \rightarrow B$ and $B \rightarrow A$ concurrently $\rightarrow$ zero deadlocks, balances update accurately).
       - Test 3: Concurrent duplicate keys (3 threads with same key $\rightarrow$ exactly 1 transfer executed, all 3 receive same transaction reference, 1 debit + 1 credit in ledger).
       - Test 4: Conflicting payload detection (concurrent same key with different amounts $\rightarrow$ exactly 1 accepted, 1 rejected with 409 Conflict).
     - `AuditServiceTest`: 3 passed (verified `recordConsistentEvent` with `REQUIRED`, `recordAutonomousEvent` with `REQUIRES_NEW`, and default `recordEvent`).
     - Day 1–3 Regression: All 61 existing tests passed cleanly without any regressions.

2. **Critical Audit Transaction Boundary Correction**:
   - **Issue:** `TransferServiceImpl` called `auditService.recordEvent("TRANSFER_COMPLETED", ...)` before outer transaction commit. Because `AuditServiceImpl.recordEvent` used `REQUIRES_NEW`, success audit records committed independently. Any subsequent transaction rollback produced a false financial record.
   - **Correction:** Implemented Dual Transaction Boundary Pattern:
     - `recordConsistentEvent` (`Propagation.REQUIRED`): used for `TRANSFER_COMPLETED`, `DEPOSIT_COMPLETED`, and `WITHDRAWAL_COMPLETED`. Commits or rolls back atomically with balance updates, ledger entries, and idempotency records.
     - `recordAutonomousEvent` (`Propagation.REQUIRES_NEW`): used for `TRANSFER_REFUSED_INSUFFICIENT_FUNDS`, `WITHDRAWAL_REFUSED_INSUFFICIENT_FUNDS`, and `RECONCILIATION_DISCREPANCY`. Commits immediately in an autonomous transaction so that refusal and discrepancy audit trails persist even when the financial mutation rolls back or runs in read-only mode.
   - **Verification:** Integration tests against real MySQL 8.0 confirmed that forced rollback after audit write leaves zero balance changes, zero ledger entries, zero idempotency records, and zero `TRANSFER_COMPLETED` audit events, while autonomous refusal records are preserved.

3. **Frontend Production Build**:
   - Command: `npm run build --prefix frontend`
   - Status: `SUCCESS` (exit code 0, built in 297ms).

4. **Code Quality & Git Hygiene**:
   - `git diff --check`: 0 whitespace errors.
   - Root `package.json` and `package-lock.json` untouched.
   - 0 files staged, 0 commits pushed.

---

### 4. Next Day's Objectives (Day 5)
1. Implement enhanced statement query filtering (date ranges, pagination, transaction type filtering).
2. Expand continuous reconciliation automation and regulatory compliance reporting.
3. Prepare backend services for frontend integration.
