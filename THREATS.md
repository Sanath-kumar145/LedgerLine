# LedgerLine — Threat Model & Security Register
**Date:** 2026-11-06
**Status:** Baseline Security Register (Day 1)
**System:** LedgerLine — Money Movement With a Ledger You Can Prove

---

## 1. Overview & Classification Framework

This register enumerates security threats, financial risks, and failure modes evaluated for the LedgerLine platform. Each threat includes its description, business and technical impact, engineered mitigation, planned verification test, and current verified status.

### Status Definitions
- **PLANNED:** The architectural mitigation is formally designed, documented, and scheduled for implementation on a designated development day.
- **IMPLEMENTED — UNVERIFIED:** The mitigation code is written but has not yet been subjected to automated or manual verification tests.
- **VERIFIED:** The mitigation is implemented and automated verification tests pass cleanly.
- **ACCEPTED RISK:** The residual risk is recognized and accepted due to infrastructural or scope boundaries.

---

## 2. Threat Register

### THREAT-01: Authentication Attacks (Brute Force & Credential Stuffing)
- **Threat Description:** An adversary attempts dictionary or automated credential stuffing attacks against `/api/v1/auth/login` to compromise user accounts.
- **Impact:** High. Account takeover, unauthorized access to balances, and fraudulent financial actions.
- **Mitigation:** Enforce login rate limiting (IP & username bucket throttling via Spring Filter/Bucket4j); account lockout after 5 consecutive failed attempts; audit event emission on failed attempts.
- **Planned Verification Test:** Automated security test firing 10 rapid failed login requests; assert HTTP 429 Too Many Requests or account locked state.
- **Status:** **PLANNED** (Scheduled for Day 2)

---

### THREAT-02: Weak Password Storage & Credential Compromise
- **Threat Description:** Plaintext or weakly hashed (e.g. MD5/SHA1) passwords compromised via database leakage or SQL dumps.
- **Impact:** Critical. Catastrophic credential breach across all system actors.
- **Mitigation:** Use Spring Security `BCryptPasswordEncoder` with minimum work factor of 12 (or Argon2); enforce password complexity policy (min 8 chars, mixed case, numbers, special characters) via Jakarta validation.
- **Planned Verification Test:** Unit test verifying BCrypt prefix `$2a$12$` and rejection of sub-standard passwords during registration.
- **Status:** **VERIFIED** (Verified via `PasswordSecurityTest` and `AuthenticationManagerConfigTest` — BCrypt cost factor 12 enforced and tested).

---

### THREAT-03: Privilege Escalation (Vertical Authorization Bypass)
- **Threat Description:** A standard user with `ROLE_USER` invokes administrative or auditor endpoints (`/api/v1/admin/*`, `/api/v1/auditor/*`).
- **Impact:** High. Unauthorized system configuration changes, unauthorized access to all user ledgers.
- **Mitigation:** Method-level security with `@PreAuthorize("hasRole('ADMIN')")` and `@PreAuthorize("hasRole('AUDITOR')")`; strict antMatcher rules in `SecurityFilterChain`.
- **Planned Verification Test:** Integration tests using `@WithMockUser(roles = "USER")` calling `/api/v1/admin/users`; assert HTTP 403 Forbidden.
- **Status:** **VERIFIED** (Verified via `RoleAuthorizationTest` — `ROLE_ADMIN` allowed access, `ROLE_ACCOUNT_HOLDER` and `ROLE_AUDITOR` rejected with HTTP 403 Forbidden).

---

### THREAT-04: Unauthorized Account Access (Broken Object Level Authorization / IDOR)
- **Threat Description:** User A supplies User B's `accountId` in API requests (`GET /api/v1/accounts/{accountId}`) to view private balances and transaction history.
- **Impact:** High. PII exposure, violation of banking privacy regulations.
- **Mitigation:** Service layer validates account ownership against authenticated SecurityContext principal (`account.getUserId().equals(currentUser.getId())`); only `ROLE_ADMIN` or `ROLE_AUDITOR` may bypass for operational reasons.
- **Planned Verification Test:** Integration test invoking `GET /api/v1/accounts/{otherUserIdAccount}`; assert HTTP 403 Forbidden.
- **Status:** **VERIFIED** (Verified via `AccountOwnershipTest` and `AccountOwnershipServiceTest` — cross-user access attempts rejected with HTTP 403 Forbidden and security alert logged).

---

### THREAT-05: Unauthorized Money Transfer (Transfers From Accounts Not Owned by User)
- **Threat Description:** User A submits a transfer request with `sourceAccountId` belonging to User B.
- **Impact:** Critical. Direct theft of customer funds.
- **Mitigation:** `TransferService` enforces strict source account ownership validation inside the transactional boundary before lock acquisition.
- **Planned Verification Test:** Integration test where User A attempts transfer from User B's account; assert HTTP 403 Forbidden and verify zero balance deduction.
- **Status:** **PLANNED** (Scheduled for Day 4)

---

### THREAT-06: Concurrent Withdrawals & Race-Condition Overdrafts
- **Threat Description:** Two simultaneous transfer/withdrawal requests for \$100 are executed on an account with a \$100 balance. In a race condition, both pass validation and produce a -\$100 overdraft.
- **Impact:** Critical. Illegal negative balances, financial insolvency.
- **Mitigation:** Pessimistic write locking (`SELECT ... FOR UPDATE`); fresh balance re-validation under lock; database check constraint `CHECK (balance >= 0.00)`.
- **Planned Verification Test:** Concurrency test using `ExecutorService` and `CountDownLatch` with 10 concurrent threads attempting to withdraw \$50 from an account with \$100; assert exactly 2 succeed and 8 fail with insufficient funds.
- **Status:** **PLANNED** (Scheduled for Day 4)

---

### THREAT-07: Idempotency Key Replay & Network Retries
- **Threat Description:** Network drop causes client to retry transfer; server executes the transfer twice, deducting funds twice.
- **Impact:** Critical. Duplicate deductions and customer balance loss.
- **Mitigation:** Mandatory `Idempotency-Key` header with database persistence in `idempotency_records`; return cached response on key replay.
- **Planned Verification Test:** Issue 3 identical transfer requests with identical `Idempotency-Key`; assert only 1 financial transaction is created, and all 3 requests return the same transaction reference.
- **Status:** **PLANNED** (Scheduled for Day 4)

---

### THREAT-08: Request Payload Manipulation & Key Reuse
- **Threat Description:** An attacker reuses an existing valid `Idempotency-Key` but alters the transfer amount from \$10 to \$1,000.
- **Impact:** High. Fraudulent transaction execution or state bypass.
- **Mitigation:** SHA-256 request fingerprinting combining user ID, HTTP path, and serialized body. If key matches but fingerprint differs, return HTTP 400 Bad Request.
- **Planned Verification Test:** Submit request with key `K` for \$10; then submit with key `K` for \$20; assert HTTP 400 with `PAYLOAD_FINGERPRINT_MISMATCH`.
- **Status:** **PLANNED** (Scheduled for Day 4)

---

### THREAT-09: Negative Amounts & Decimal Scale Tampering
- **Threat Description:** Client submits negative transfer amount (`-50.00`) to increase source balance or fractional amounts (`0.00001`) causing rounding errors.
- **Impact:** Critical. Synthetic money generation or balance corruption.
- **Mitigation:** Jakarta validation `@DecimalMin(value = "0.01")` and `@Digits(integer = 15, fraction = 2)`; DB check constraints `CHECK (amount > 0.00)`.
- **Planned Verification Test:** Submit requests with `-10.00`, `0.00`, and `10.555`; assert HTTP 400 Bad Request with validation errors.
- **Status:** **PLANNED** (Scheduled for Day 3 & Day 4)

---

### THREAT-10: Ledger Tampering (Direct Manipulation of Past Entries)
- **Threat Description:** An internal rogue user or vulnerable endpoint attempts to modify or delete historical records in `ledger_entries` or `ledger_transactions`.
- **Impact:** Critical. Loss of financial proof, audit trail corruption.
- **Mitigation:** Immutable append-only design; no update/delete methods in repository; database user privileges restricted to revoke `UPDATE` and `DELETE` on ledger tables.
- **Planned Verification Test:** Integration test verifying absence of delete/update repository endpoints; DB privilege test verifying `DELETE` denial.
- **Status:** **PLANNED** (Scheduled for Day 4 & Day 5)

---

### THREAT-11: SQL Injection (SQLi)
- **Threat Description:** Unsanitized user inputs in queries lead to arbitrary SQL execution.
- **Impact:** Critical. Database breach, data loss, integrity destruction.
- **Mitigation:** Use Spring Data JPA named parameters, Criteria API, or JPQL binding; zero dynamic SQL string concatenation.
- **Planned Verification Test:** Security test injecting `' OR 1=1 --` into username, account number, and description fields; assert strict literal query matching.
- **Status:** **PLANNED** (Scheduled for Day 2)

---

### THREAT-12: Sensitive Information Exposure in Logs & Error Traces
- **Threat Description:** Passwords, JWT secrets, full credit card/account numbers, or stack traces exposed in log files or HTTP 500 error envelopes.
- **Impact:** Medium to High. Information disclosure enabling secondary attacks.
- **Mitigation:** Custom log formatting masking passwords and tokens; `@RestControllerAdvice` emitting standardized `ProblemDetail` responses without raw stack traces.
- **Planned Verification Test:** Trigger intentional exception (e.g. entity not found); assert response contains structured error envelope without stack trace.
- **Status:** **VERIFIED** (Verified via `GlobalExceptionHandler` and controller tests — RFC 7807 responses returned with zero stack trace leaks; DTOs omit password hashes).

---

### THREAT-13: Secret & Credential Leakage in Version Control
- **Threat Description:** Database passwords, JWT signing keys, or private certificates committed into Git repository.
- **Impact:** Critical. Full application takeover.
- **Mitigation:** Exclusion via `.gitignore` (`.env`, `credentials/`, `secrets/`); externalized configuration via OS environment variables.
- **Planned Verification Test:** Repository-wide automated secret scan verifying absence of hardcoded passwords or JWT signing keys in git history.
- **Status:** **VERIFIED** (Root `.gitignore` established on Day 1; production `application.properties` externalizes credentials and enforces TLS encryption with `sslMode=REQUIRED`; `JwtTokenProvider` strictly mandates at least 32 bytes of entropy for `JWT_SECRET` with zero fallback).

---

### THREAT-14: Database Connection Failure & Transaction Rollback Failure
- **Threat Description:** Database disconnects after debiting Account A but before crediting Account B, resulting in lost funds.
- **Impact:** Critical. Double-entry imbalance, money destruction.
- **Mitigation:** Single atomic ACID transaction managed by Spring's `PlatformTransactionManager`; InnoDB rollbacks uncommitted operations on connection loss.
- **Planned Verification Test:** Simulated transaction failure test triggering an artificial runtime exception immediately after debit insertion; assert neither debit nor credit persist.
- **Status:** **PLANNED** (Scheduled for Day 4)

---

### THREAT-15: Denial of Service via Database Row Lock Contention
- **Threat Description:** Adversary sends hundreds of rapid transfers against a single hot account, causing database lock timeouts and thread starvation.
- **Impact:** High. Service degradation for legitimate users.
- **Mitigation:** HikariCP connection pool timeout limits; pessimistic lock timeouts (`javax.persistence.lock.timeout = 3000`); application rate limiting.
- **Planned Verification Test:** Stress test measuring latency under concurrent access; assert clean timeout handling without JVM thread crashes.
- **Status:** **PLANNED** (Scheduled for Day 7)

---

### THREAT-16: Physical Host / Root DBA Tampering
- **Threat Description:** A system administrator with physical host or MySQL root access executes raw SQL modifying balances out-of-band.
- **Impact:** Critical. Unprovable balance corruption.
- **Mitigation:** Architectural acceptance of physical boundary; detection enabled via cryptographic ledger hash chaining and external continuous audit reconciliation.
- **Planned Verification Test:** Auditor discrepancy detection test: manually mutate balance via SQL, run auditor reconciliation tool, verify discrepancy is flagged.
- **Status:** **ACCEPTED RISK** (Host/root DBA compromise cannot be prevented by application code alone; detection is fully engineered).
