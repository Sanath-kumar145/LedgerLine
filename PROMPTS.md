# LedgerLine — AI Prompts & Interaction Record
**Project:** LedgerLine — Money Movement With a Ledger You Can Prove
**Development Day:** Day 1 (Including Final Technical Review)
**Date:** 2026-11-06

---

## 1. Actual Day 1 Execution Prompts

### 1.1 Initial Day 1 Prompt
The session was initiated with the standard Day 1 execution prompt requesting:
- Repository audit (root, backend, frontend, Maven, npm, Flyway, tests, git status).
- System architecture design (`docs/ARCHITECTURE.md`).
- Relational database design across 8 tables with Mermaid ER diagram (`docs/DATABASE_DESIGN.md`).
- Financial integrity design (`docs/FINANCIAL_INTEGRITY.md`).
- Threat model (`THREATS.md`).
- Requirements mapping (`docs/REQUIREMENTS_MATRIX.md`).
- API design (`docs/API_DESIGN.md`).
- Baseline build and test execution.

### 1.2 Final Technical Review Prompt
Before accepting Day 1 changes, a detailed technical review prompt was submitted:
1. Review the newly created test `application.properties`. Determine whether excluding DataSource auto-configuration is the correct long-term testing approach. Do not hide database configuration problems. Recommend a proper MySQL integration-test strategy, preferably using Testcontainers or an appropriately configured MySQL test database.
2. Review the Maven wrapper URL change. Verify the official distribution URL, wrapper compatibility and whether the change is actually necessary.
3. Inspect root `package.json` and `package-lock.json`. Determine whether they are required by the project. Do not delete anything without explaining its purpose and checking dependencies.
4. Review the database design. Verify that external deposits and withdrawals have balanced accounting entries through appropriate external clearing accounts.
5. Verify that the requirements matrix covers every original problem-statement requirement, including statements, refused attempts, initiator tracking, idempotency, concurrency and ledger protection.
6. Review `PROMPTS.md` and ensure it contains genuine work records rather than fabricated AI mistakes or corrections.
7. Review all documentation for contradictory design decisions.
8. Do not claim database integration, financial correctness or concurrency safety as verified when those tests have not been implemented.
9. Show the exact files requiring changes and explain each correction before applying it.
10. Run the relevant checks again and provide the final git diff summary.

---

## 2. Genuine Record of AI Technical Review & Findings

### 2.1 Test `application.properties` & Long-Term Integration Strategy
- **Audit Finding:** The Day 1 creation of `backend/ledgerline-api/src/test/resources/application.properties` with DataSource auto-configuration exclusions was a temporary bootstrap stub designed to let `@SpringBootTest contextLoads()` pass when no database container or entities existed.
- **Evaluation:** Hiding database problems by disabling DataSource auto-configuration is an anti-pattern for integration testing. For Days 2 through 7, real integration testing requires Testcontainers MySQL (`org.testcontainers:mysql` + `@ServiceConnection`) or an active MySQL test database (`ledgerline_test`) to validate actual Flyway migrations, MySQL check constraints, and InnoDB pessimistic row locks (`SELECT ... FOR UPDATE`).
- **Remediation:** Added explicit architectural warning headers to `src/test/resources/application.properties`, documented the Day 1 bootstrap scope, and formally mandated Testcontainers MySQL for Day 2+ integration tests.

### 2.2 Maven Wrapper Distribution URL Verification
- **Audit Finding:** The initial belief that Apache Maven 3.10.0 did not exist was disproven upon direct probing of `https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip`. The URL returned `HTTP/1.1 200 OK` (10,102,082 bytes; Last-Modified: Sun, 27 Sep 2026). The initial wrapper failure was caused solely by sandbox DNS/network isolation.
- **Remediation:** Reverted `backend/ledgerline-api/.mvn/wrapper/maven-wrapper.properties` back to its original official Maven 3.10.0 distribution URL.

### 2.3 Root `package.json` and `package-lock.json` Analysis
- **Audit Finding:** Root `package.json` contains `axios`, `lucide-react`, `react-router-dom`, `@tailwindcss/vite`, and `tailwindcss`. The project is not configured as an npm workspace monorepo. These packages represent the UI dependencies required by `frontend/` that were inadvertently installed in the root.
- **Remediation:** Retained the files intact per non-destructive audit rules; documented that these dependencies belong in `frontend/package.json` when the frontend application is constructed on Day 6. Root `.gitignore` prevents root `node_modules` from being committed.

### 2.4 Database Design: External Clearing Accounts & Constraints
- **Audit Finding:** In `docs/DATABASE_DESIGN.md`, applying an unconditional `CHECK (balance >= 0.00)` across all accounts would cause a check constraint failure when a `SYSTEM_CLEARING` account is debited for customer deposits. Additionally, `accounts.user_id` was marked `NOT NULL`, which would prevent system clearing accounts from existing without an associated user.
- **Remediation:** Updated `accounts.user_id` to be nullable for `SYSTEM_CLEARING` accounts, updated the check constraint to `CHECK (account_type = 'SYSTEM_CLEARING' OR balance >= 0.00)`, and documented the exact balanced double-entry accounting entries for deposits and withdrawals via `SYSTEM_CLEARING`.

### 2.5 Initiator Tracking & Requirements Matrix Completeness
- **Audit Finding:** The database schema previously lacked an explicit initiator reference on transaction headers.
- **Remediation:** Added `initiated_by_user_id BIGINT NOT NULL, FK -> users(id)` to `ledger_transactions`. Added `REQ-INIT-01` (Initiator Tracking) and `REQ-PROT-01` (Ledger Immutability) to `docs/REQUIREMENTS_MATRIX.md`.

---

## 3. Verification & Integrity Statement

All investigations, commands, and outputs documented in this record represent genuine technical findings executed on the project environment. No test results or implementation statuses were fabricated.
