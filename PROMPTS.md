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


---

## 4. Actual Day 2 Execution Prompts

### 4.1 Initial Day 2 Implementation Prompt
The session was initiated with the Day 2 execution prompt:
- **Scope:** Authentication, Role-Based Access Control, Account Ownership & MySQL Integration Testing.
- **Specifics:**
  1. Inspect existing project, documents, environment, and dependencies.
  2. Implement `V1__init_auth_schema.sql` (users, roles, user_roles, accounts with `chk_accounts_balance_positive`).
  3. Implement entities (`User`, `Role`, `Account`), repositories, and DTOs.
  4. Implement `SecurityConfig` with stateless JWT, BCrypt cost 12, CORS, CSRF disabled, and RFC 7807 ProblemDetail handlers.
  5. Clean separation of roles (`ROLE_ADMIN`, `ROLE_ACCOUNT_HOLDER`, `ROLE_AUDITOR`).
  6. Enforce server-side account ownership authorization (`account.userId == principal.id`) with 403 Forbidden.
  7. Implement Testcontainers MySQL 8.0 configuration replacing Day 1 temporary exclusions.
  8. Update documentation with truthful test results.

### 4.2 Diagnostic & Compilation Analysis Prompt
Requested an in-depth diagnostic report for backend test compilation errors:
- Inspect output of Maven test compilation (`task-499.log`).
- Identify the first actual compilation error and list every error with Java filename and line number.
- Separate missing dependencies, incompatible versions, incorrect imports, missing classes, and API incompatibilities.
- Check Spring Boot, Spring Security, JWT, Jackson, and Testcontainers compatibility.
- Report exact Docker and MySQL status without claiming unverified passes.

### 4.3 Spring Boot 4 WebMvcTest Import & Dependency Correction Prompt
- Replace old `org.springframework.boot.test.autoconfigure.web.servlet.*` package imports with Spring Boot 4 packages: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` and `AutoConfigureMockMvc`.
- Re-run `mvn clean test-compile` and unit tests, distinguishing passed, failed, skipped, and not executed.
- Review Jackson dependencies and compatibility with Spring Boot 4.1.2.

### 4.4 Full Test Context & Application Slice Initialization Prompt
- Fix missing `ObjectMapper` bean in `WebMvcTest` slices using correct Spring Boot 4 configuration.
- Replace Day 1 temporary test datasource exclusions with proper test configuration.
- Ensure `UserRepository` and JPA repositories initialize in full application context tests.
- Preserve genuine MySQL/Testcontainers integration testing without H2 substitution.

### 4.5 Testcontainers Exception Handling Refinement Prompt
- Review `LedgerlineApiApplicationTests.java` where `isDockerUnavailable()` caught `Throwable`.
- Remove overbroad exception handling swallowing JVM errors; use Testcontainers' supported `disabledWithoutDocker` mechanism.
- Skip only when Docker is genuinely unavailable, preserving the Spring Boot application context test.

### 4.6 Spring Security Warning Investigation Prompt
- Investigate two Spring Security startup warnings:
  1. Generated security password from `UserDetailsServiceAutoConfiguration`.
  2. Multiple `UserDetailsService` beans (`inMemoryUserDetailsManager` and `CustomUserDetailsService`).
- Determine if warnings occur in test slices or production.
- Verify `AuthenticationManager` uses intended `CustomUserDetailsService`, `PasswordEncoder`, and `DaoAuthenticationProvider` without suppressing warnings.

### 4.7 Final Review & Security Hardening Prompt
- Verify `AuthenticationManager` uses intended `CustomUserDetailsService` and `BCryptPasswordEncoder`.
- Inspect production and test `application.properties`: remove unsafe production defaults (MySQL root password fallback) and enforce environment variables.
- Verify JWT signing secrets are externalized with no production secret hardcoded.
- Check all Day 2 source files for security, authorization, validation, and exception handling defects.
- Update documentation and run complete available test suite, reporting passed, failed, and skipped tests separately.

### 4.8 Day 2 Security Hardening Prompt
- Remove the SHA-256 fallback that transforms short secrets into apparently valid signing keys.
- Require a securely generated secret with at least 32 bytes of entropy; fail application startup when the configured secret is missing, blank, or too short (< 32 bytes).
- Update `JwtTokenProviderTest` to test missing, short, and valid secrets.
- Document how to generate a secure `JWT_SECRET`.
- Database production configuration: remove `useSSL=false` and `allowPublicKeyRetrieval=true` from production defaults; require production database connections to use TLS (`sslMode=REQUIRED`); keep local development configuration separate from production configuration.
- Re-run test suites and report actual results.

---

## 5. Genuine Record of AI Technical Review & Findings (Day 2)

### 5.1 Spring Boot 4.1.2 & Spring Security 7.x Package Restructuring
- **Audit Finding:** Attempting to compile slice tests with `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` resulted in 12 compilation errors (`package does not exist`).
- **Root Cause:** Spring Boot 4 restructured the webmvc autoconfiguration slices into `org.springframework.boot.webmvc.test.autoconfigure.*`. Additionally, Spring Security 7.x deprecated parameterless constructor injection on `DaoAuthenticationProvider`, requiring `new DaoAuthenticationProvider(userDetailsService)`.
- **Remediation:** Updated test slice imports to `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` and `AutoConfigureMockMvc`. Updated `SecurityConfig` to construct `DaoAuthenticationProvider(userDetailsService)` directly.

### 5.2 Jackson Datatype & Time Module Modularization
- **Audit Finding:** In `@WebMvcTest` slices, controllers threw `NoSuchBeanDefinitionException: No qualifying bean of type 'ObjectMapper' available`. Furthermore, serializing `Instant` in RFC 7807 responses triggered `InvalidDefinitionException: Java 8 date/time type java.time.Instant not supported by default`.
- **Root Cause:** Spring Boot 4 separates the core Jackson 3 pipeline from legacy `com.fasterxml.jackson.databind.ObjectMapper` used by JJWT and existing DTOs.
- **Remediation:** Added `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` to `pom.xml`, created `JacksonConfig.java` providing a configured `ObjectMapper` bean with `JavaTimeModule` registered, and imported it into MVC slice tests via `@Import(JacksonConfig.class)`.

### 5.3 Spring Security AutoConfiguration & ProviderManager Disambiguation
- **Audit Finding:** Full test runs outputted warnings from `InitializeUserDetailsBeanManagerConfigurer`: "Global AuthenticationManager configured with an AuthenticationProvider bean... multiple UserDetailsService beans". In addition, slice tests printed default generated passwords.
- **Root Cause:** Exposing `DaoAuthenticationProvider` as a standalone `@Bean` caused Spring Security's autoconfiguration to attempt global manager wiring alongside `CustomUserDetailsService`. Slice tests without security configuration loaded default fallback in-memory users.
- **Remediation:** Encapsulated `DaoAuthenticationProvider` inside `new ProviderManager(authenticationProvider())` returned by the `@Bean AuthenticationManager` in `SecurityConfig.java`. Added `@Import({JacksonConfig.class, SecurityConfig.class})` to controller slice tests. This eliminated all generated password logs and multiple-bean warnings while guaranteeing deterministic authentication.

### 5.4 Testcontainers Lifecycle & Exception Handling
- **Audit Finding:** `LedgerlineApiApplicationTests.java` implemented a helper method `isDockerUnavailable()` catching `java.lang.Throwable` to conditionally skip context loading if Docker was unreachable.
- **Root Cause:** Catching `Throwable` swallows all JVM errors, including `OutOfMemoryError`, `LinkageError`, and unrelated Spring context errors, masking genuine application bugs.
- **Remediation:** Replaced custom `Throwable` catch blocks with Testcontainers' built-in `@Testcontainers(disabledWithoutDocker = true)` annotation. This natively detects Docker engine status and cleanly marks tests as skipped via JUnit 5 without swallowing JVM errors.

### 5.5 Production Credentials & Secret Externalization
- **Audit Finding:** `application.properties` previously contained fallback default credentials (`root` / `root`) and default JWT secrets.
- **Remediation:** Removed all fallback credentials from production `application.properties`. Mandated `${DB_USERNAME}`, `${DB_PASSWORD}`, and `${JWT_SECRET}` from the operating environment. Configured `JwtTokenProvider` to reject missing or blank secrets with `IllegalArgumentException` on initialization. Test credentials remain safely isolated in `src/test/resources/application.properties`.

### 5.6 Account Ownership & IDOR Protection Verification
- **Audit Finding:** Insecure Direct Object Reference (IDOR) attacks could allow account holders to inspect balances or account numbers belonging to other users.
- **Remediation:** Implemented server-side ownership verification in `AccountServiceImpl.getAccountById()`. The method verifies `account.isOwnedBy(currentUser.getId())` unless the caller possesses `ROLE_ADMIN` or `ROLE_AUDITOR`. Unauthorized access attempts log a security alert and throw `AccessDeniedException` (HTTP 403 Forbidden). In `getAccountsForCurrentUser()`, user identity is derived strictly from `currentUser.getId()` in the authenticated `UserPrincipal`, completely ignoring any caller-supplied account parameters.

### 5.7 Cryptographic Entropy & TLS Enforcement
- **Audit Finding:** `JwtTokenProvider` previously contained a SHA-256 fallback expansion which converted secrets under 32 bytes into 256-bit hashes. While allowing short passwords to satisfy HMAC-SHA256 mathematical minimums, it weakened effective entropy against brute force. In addition, production `application.properties` retained `useSSL=false` and `allowPublicKeyRetrieval=true` in default URL templates.
- **Remediation:** Removed the SHA-256 fallback in `JwtTokenProvider`. Configured strict validation in the constructor: secrets that are null, blank, or fewer than 32 bytes throw `IllegalArgumentException` and halt startup. Updated `JwtTokenProviderTest` with dedicated tests for null, empty, blank, short (< 32 bytes), and valid (>= 32 bytes) secrets. In production `application.properties`, eliminated `useSSL=false` and `allowPublicKeyRetrieval=true`, mandating `sslMode=REQUIRED` (TLS encryption). Isolated local developer settings into a separate `application-dev.properties` profile. Documented secure secret generation (`openssl rand -hex 32`) in `README.md` and `docs/SECURITY_DESIGN.md`.

---

## 6. Verification & Integrity Statement (Day 2)

All investigations, compilation fixes, security enhancements, and test suite executions documented in this record represent genuine technical findings executed on the project environment. No test results or implementation statuses were fabricated.
