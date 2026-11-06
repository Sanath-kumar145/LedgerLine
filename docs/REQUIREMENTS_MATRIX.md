# LedgerLine — Requirements Traceability Matrix
**Date:** 2026-11-06
**Status:** Baseline Specification (Day 1 Technical Review)
**System:** LedgerLine — Money Movement With a Ledger You Can Prove

---

## 1. Overview

This matrix maps all core functional and non-functional requirements of the LedgerLine platform to their engineered architecture, target implementation modules, automated verification strategies, and current lifecycle status.

### Status Definitions
- **PLANNED:** Formal architecture and design complete; scheduled for implementation on a designated development day.
- **IN PROGRESS:** Implementation currently underway.
- **VERIFIED:** Implemented and verified via automated tests.

---

## 2. Requirements Mapping Matrix

| Req ID | Domain Category | Requirement Description | Planned Implementation | Expected Verification Test | Status | Target Day |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **REQ-AUTH-01** | Authentication | Users must register with unique username, email, and strong password. | `AuthController.register()`, `UserService`, `BCryptPasswordEncoder` | Unit & API tests for registration validation, duplicate email/username rejection | **PLANNED** | Day 2 |
| **REQ-AUTH-02** | Authentication | Users must authenticate via credentials and receive a signed JWT. | `AuthController.login()`, `JwtTokenProvider`, `AuthenticationManager` | Login tests with valid/invalid credentials; JWT signature & claims verification | **PLANNED** | Day 2 |
| **REQ-AUTH-03** | Authorization | System must support three distinct roles: `USER`, `ADMIN`, `AUDITOR`. | Spring Security RBAC, `Role` entity, `SecurityFilterChain` | Method security tests verifying role enforcement on protected endpoints | **PLANNED** | Day 2 |
| **REQ-ACCT-01** | Account Ownership | Users can hold one or more accounts (`CHECKING`, `SAVINGS`). | `Account` entity, `AccountService.createAccount()`, `AccountRepository` | Account creation tests verifying initial zero balance and user linkage | **PLANNED** | Day 3 |
| **REQ-ACCT-02** | Account Ownership | Users can only inspect accounts they own (unless caller is ADMIN/AUDITOR). | Service-layer ownership verification (`account.userId == principal.id`) | Broken Object Level Authorization (IDOR) tests returning HTTP 403 on cross-user reads | **PLANNED** | Day 3 |
| **REQ-MONEY-01** | Money Precision | Monetary amounts must use 2-decimal fixed-point precision (`DECIMAL(19,2)`). | MySQL `DECIMAL(19,2)`, Java `BigDecimal` with `RoundingMode.UNNECESSARY` | Precision tests verifying rejection of floating-point truncation or extra fraction digits | **PLANNED** | Day 3 |
| **REQ-DEP-01** | Funding | Users can deposit funds into their account from a clearing account. | `TransferService.deposit()`, double-entry credit to user account, debit to `SYSTEM_CLEARING` | Deposit integration test verifying user balance increase and balanced double-entry clearing | **PLANNED** | Day 3 |
| **REQ-WTH-01** | Funding | Users can withdraw funds from their account up to available balance. | `TransferService.withdraw()`, double-entry debit to user account, credit to `SYSTEM_CLEARING` | Withdrawal integration test; verify balance deduction, clearing credit, and rejection when funds insufficient | **PLANNED** | Day 3 |
| **REQ-XFER-01** | Transfers | Users can transfer funds between internal accounts atomically. | `TransferService.transfer()`, single `@Transactional` method executing double-entry | Transfer test verifying sender balance decreased, receiver increased by identical amount | **PLANNED** | Day 4 |
| **REQ-CONS-01** | Conservation | Total money across internal transfers must be conserved ($\sum \text{Debit} = \sum \text{Credit}$). | `LedgerTransaction` + `LedgerEntry` double-entry journal engine | Double-entry invariant test asserting total debit postings equal total credit postings | **PLANNED** | Day 4 |
| **REQ-NOOD-01** | Overdraft Protection | Customer account balances must never fall below zero under any condition. | Application balance check under lock + DB constraint `CHECK (account_type = 'SYSTEM_CLEARING' OR balance >= 0.00)` | Overdraft rejection test; verify `InsufficientFundsException` and DB constraint enforcement | **PLANNED** | Day 4 |
| **REQ-IDEM-01** | Idempotency | Mutating financial operations must accept client `Idempotency-Key` and prevent duplicate execution. | `IdempotencyInterceptor`, `idempotency_records` table, SHA-256 fingerprinting | Replay test issuing identical transfer requests; assert only 1 execution and duplicate receives cached response | **PLANNED** | Day 4 |
| **REQ-IDEM-02** | Idempotency | Reusing an idempotency key with altered payload must be rejected. | SHA-256 request fingerprint validation | Fingerprint mismatch test verifying HTTP 400 Bad Request on modified payload | **PLANNED** | Day 4 |
| **REQ-CONC-01** | Concurrency | Concurrent transfers must execute safely without race conditions or negative balances. | Pessimistic locking (`SELECT ... FOR UPDATE`), fresh read under lock | Multi-threaded test with 10 concurrent threads contending for same balance; verify zero overdraft | **PLANNED** | Day 4 |
| **REQ-CONC-02** | Concurrency | Bidirectional concurrent transfers must not trigger deadlocks. | Deterministic locking order: lock $\min(\text{src}, \text{dst})$ then $\max(\text{src}, \text{dst})$ | Bidirectional transfer stress test (A->B and B->A concurrently); verify zero deadlocks | **PLANNED** | Day 4 |
| **REQ-INIT-01** | Initiator Tracking | Every financial transaction must record the authenticated user who initiated it. | `ledger_transactions.initiated_by_user_id` FK reference to `users(id)` | Transaction inspection test verifying initiator ID is populated and matches authenticated principal | **PLANNED** | Day 4 |
| **REQ-PROT-01** | Ledger Immutability | Historical ledger records must be strictly append-only; updates and deletes prohibited. | Repository design omitting update/delete methods; DB user privileges revoking `UPDATE`/`DELETE` on ledger tables | Integration test attempting to delete or mutate an existing ledger entry; assert failure | **PLANNED** | Day 4 |
| **REQ-HIST-01** | Statements | Users must be able to view an immutable statement of all account ledger entries. | `StatementService.getStatement()`, ordered query on `ledger_entries` | Statement retrieval test verifying all credits, debits, and running balances match | **PLANNED** | Day 5 |
| **REQ-AUDT-01** | Audit Trail | Refused transactions (e.g. insufficient funds) must be permanently logged in audit trail. | `AuditService.recordRefusedAttempt()`, `audit_events` table | Refused transfer test verifying entry persisted in `audit_events` with reason code | **PLANNED** | Day 5 |
| **REQ-RECON-01** | Reconciliation | An auditor must be able to mathematically prove that account balance equals sum of ledger entries. | `ReconciliationService.reconcileAccount()`, SQL sum verification | Reconciliation test verifying zero discrepancy across all active accounts | **PLANNED** | Day 5 |
| **REQ-UI-01** | Frontend | Responsive user interface for dashboard, transfers, statements, and auditor console. | React 19, TypeScript, Vite, TailwindCSS, role-based navigation | Frontend build and component tests; end-to-end user workflow verification | **PLANNED** | Day 6 |
| **REQ-SEC-01** | Security | Zero secrets committed in source code; input sanitization; RFC 7807 problem details. | Root `.gitignore`, environment variable injection, GlobalExceptionHandler | Repository secret scan; exception handler test verifying no stack trace leaks | **VERIFIED** (Day 1 baseline established) | Day 1 / Day 2 |
| **REQ-BLD-01** | Build Baseline | Backend and frontend must build cleanly from repository root. | Maven wrapper (3.10.0), npm scripts (`npm run build`, `npm run lint`) | Build verification commands passing with exit code 0 | **VERIFIED** | Day 1 |
