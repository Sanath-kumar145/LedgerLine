# LedgerLine — RESTful API Specification
**Date:** 2026-11-06
**Status:** Approved Design Baseline (Day 1)
**Base URL:** `/api/v1`
**Protocol:** HTTPS / JSON

---

## 1. Implementation Status Overview

| Endpoint Category | Base Path | Implementation Status | Planned Target Day |
| :--- | :--- | :--- | :--- |
| **Authentication** | `/api/v1/auth` | **IMPLEMENTED & VERIFIED** | Day 2 |
| **Users** | `/api/v1/users` | **IMPLEMENTED & VERIFIED** | Day 2 |
| **Accounts** | `/api/v1/accounts` | **IMPLEMENTED & VERIFIED** (List & Get with IDOR Protection) | Day 2 / Day 3 |
| **Admin Operations** | `/api/v1/admin/users` | **IMPLEMENTED & VERIFIED** (User Directory) | Day 2 / Day 5 |
| **Deposits & Withdrawals** | `/api/v1/accounts/{id}/deposits`, `/withdrawals` | **PLANNED** | Day 3 |
| **Transfers** | `/api/v1/transfers` | **PLANNED** | Day 4 |
| **Statements & Ledger** | `/api/v1/accounts/{id}/statement` | **PLANNED** | Day 5 |
| **Auditor Operations** | `/api/v1/auditor` | **PLANNED** | Day 5 |

*Note: As of Day 1, zero business endpoints are implemented. Only the Spring Boot framework baseline is active.*

---

## 2. Standardized Error Response Format (RFC 7807)

All non-2xx responses conform to the standard RFC 7807 Problem Detail envelope:

```json
{
  "type": "https://api.ledgerline.com/errors/insufficient-funds",
  "title": "Insufficient Funds",
  "status": 422,
  "detail": "Account ACC-10928 has available balance of 45.00 USD, which is less than the requested transfer amount of 100.00 USD.",
  "instance": "/api/v1/transfers",
  "timestamp": "2026-11-06T15:30:00.000Z",
  "errorCode": "FIN-42201"
}
```

---

## 3. Authentication & User Endpoints

### 3.1 Register User
- **Method:** `POST`
- **URL:** `/api/v1/auth/register`
- **Purpose:** Create a new user account with default `ROLE_USER`.
- **Required Role:** Public (`permitAll`)
- **Request Headers:** `Content-Type: application/json`
- **Request Body:**
  ```json
  {
    "username": "alice_smith",
    "email": "alice@example.com",
    "password": "StrongPassword123!",
    "fullName": "Alice Smith"
  }
  ```
- **Response:** `201 Created`
  ```json
  {
    "userId": 101,
    "username": "alice_smith",
    "email": "alice@example.com",
    "fullName": "Alice Smith",
    "roles": ["ROLE_USER"],
    "createdAt": "2026-11-06T15:30:00.000Z"
  }
  ```
- **Error Responses:**
  - `400 Bad Request`: Validation failure (weak password, invalid email format).
  - `409 Conflict`: Username or email already registered.

### 3.2 Authenticate / Login
- **Method:** `POST`
- **URL:** `/api/v1/auth/login`
- **Purpose:** Authenticate credentials and obtain JWT access token.
- **Required Role:** Public (`permitAll`)
- **Request Body:**
  ```json
  {
    "username": "alice_smith",
    "password": "StrongPassword123!"
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "userId": 101,
      "username": "alice_smith",
      "roles": ["ROLE_USER"]
    }
  }
  ```
- **Error Responses:**
  - `401 Unauthorized`: Invalid credentials.
  - `429 Too Many Requests`: Rate limit exceeded / Account locked.

### 3.3 Get Current User Profile
- **Method:** `GET`
- **URL:** `/api/v1/users/profile`
- **Purpose:** Retrieve the profile of the authenticated caller.
- **Required Role:** Authenticated (`ROLE_USER`, `ROLE_ADMIN`, `ROLE_AUDITOR`)
- **Request Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK`
  ```json
  {
    "userId": 101,
    "username": "alice_smith",
    "email": "alice@example.com",
    "fullName": "Alice Smith",
    "roles": ["ROLE_USER"],
    "status": "ACTIVE"
  }
  ```
- **Error Responses:**
  - `401 Unauthorized`: Missing or invalid JWT.

---

## 4. Account Management Endpoints

### 4.1 Create Account
- **Method:** `POST`
- **URL:** `/api/v1/accounts`
- **Purpose:** Open a new bank account (`CHECKING` or `SAVINGS`) for the authenticated user.
- **Required Role:** `ROLE_USER`
- **Request Body:**
  ```json
  {
    "accountType": "CHECKING",
    "currency": "USD"
  }
  ```
- **Response:** `201 Created`
  ```json
  {
    "accountId": 501,
    "accountNumber": "LL-US-8921-0001",
    "accountType": "CHECKING",
    "currency": "USD",
    "balance": "0.00",
    "status": "ACTIVE",
    "createdAt": "2026-11-06T15:35:00.000Z"
  }
  ```

### 4.2 List User Accounts
- **Method:** `GET`
- **URL:** `/api/v1/accounts`
- **Purpose:** List all accounts owned by the authenticated user.
- **Required Role:** `ROLE_USER`
- **Response:** `200 OK`
  ```json
  [
    {
      "accountId": 501,
      "accountNumber": "LL-US-8921-0001",
      "accountType": "CHECKING",
      "currency": "USD",
      "balance": "1250.50",
      "status": "ACTIVE"
    }
  ]
  ```

### 4.3 Get Account Details
- **Method:** `GET`
- **URL:** `/api/v1/accounts/{accountId}`
- **Purpose:** Retrieve account details. Enforces ownership check (IDOR protection).
- **Required Role:** `ROLE_USER` (Must own account) or `ROLE_ADMIN` / `ROLE_AUDITOR`
- **Response:** `200 OK`
  ```json
  {
    "accountId": 501,
    "accountNumber": "LL-US-8921-0001",
    "accountType": "CHECKING",
    "currency": "USD",
    "balance": "1250.50",
    "status": "ACTIVE",
    "createdAt": "2026-11-06T15:35:00.000Z"
  }
  ```
- **Error Responses:**
  - `403 Forbidden`: Account belongs to another user.
  - `404 Not Found`: Account does not exist.

---

## 5. Financial Operations Endpoints

### 5.1 Deposit Funds
- **Method:** `POST`
- **URL:** `/api/v1/accounts/{accountId}/deposits`
- **Purpose:** Inflow external funds into an account via balanced double-entry clearing.
- **Required Role:** `ROLE_USER` (Account owner)
- **Request Headers:**
  - `Authorization: Bearer <token>`
  - `Idempotency-Key: 7b34bdf8-cf12-4217-a083-09736528d223`
- **Request Body:**
  ```json
  {
    "amount": "500.00",
    "description": "Initial payroll deposit"
  }
  ```
- **Response:** `201 Created`
  ```json
  {
    "transactionReference": "TX-DEP-990214",
    "accountId": 501,
    "transactionType": "DEPOSIT",
    "amount": "500.00",
    "newBalance": "1750.50",
    "status": "COMPLETED",
    "timestamp": "2026-11-06T15:40:00.000Z"
  }
  ```
- **Error Responses:**
  - `400 Bad Request`: Invalid amount (`<= 0.00` or invalid decimal scale).
  - `409 Conflict`: Idempotency key already in-flight.

### 5.2 Withdraw Funds
- **Method:** `POST`
- **URL:** `/api/v1/accounts/{accountId}/withdrawals`
- **Purpose:** Outflow funds from an account up to available balance.
- **Required Role:** `ROLE_USER` (Account owner)
- **Request Headers:**
  - `Authorization: Bearer <token>`
  - `Idempotency-Key: e190a423-1d01-4475-bf04-6ea6354fa671`
- **Request Body:**
  ```json
  {
    "amount": "200.00",
    "description": "ATM cash withdrawal"
  }
  ```
- **Response:** `201 Created`
  ```json
  {
    "transactionReference": "TX-WTH-882190",
    "accountId": 501,
    "transactionType": "WITHDRAWAL",
    "amount": "200.00",
    "newBalance": "1550.50",
    "status": "COMPLETED",
    "timestamp": "2026-11-06T15:42:00.000Z"
  }
  ```
- **Error Responses:**
  - `422 Unprocessable Entity`: Insufficient balance (prevents overdraft).

### 5.3 Transfer Funds (Internal Double-Entry Transfer)
- **Method:** `POST`
- **URL:** `/api/v1/transfers`
- **Purpose:** Atomically transfer funds from source account to destination account.
- **Required Role:** `ROLE_USER` (Must own `sourceAccountId`)
- **Request Headers:**
  - `Authorization: Bearer <token>`
  - `Idempotency-Key: 9283fa71-551e-4509-a1b7-78da0b1e1948`
- **Request Body:**
  ```json
  {
    "sourceAccountId": 501,
    "destinationAccountId": 502,
    "amount": "350.00",
    "description": "Rent share payment"
  }
  ```
- **Response:** `201 Created`
  ```json
  {
    "transactionReference": "TX-XFER-3849102",
    "transactionType": "TRANSFER",
    "sourceAccountId": 501,
    "destinationAccountId": 502,
    "amount": "350.00",
    "status": "COMPLETED",
    "timestamp": "2026-11-06T15:45:00.000Z",
    "entries": [
      {
        "accountId": 501,
        "entryType": "DEBIT",
        "amount": "350.00",
        "balanceAfter": "1200.50"
      },
      {
        "accountId": 502,
        "entryType": "CREDIT",
        "amount": "350.00",
        "balanceAfter": "850.00"
      }
    ]
  }
  ```
- **Error Responses:**
  - `400 Bad Request`: Same source and destination account ID.
  - `403 Forbidden`: User does not own source account.
  - `422 Unprocessable Entity`: Insufficient balance in source account.
  - `409 Conflict`: Concurrent request with same idempotency key currently in progress.

---

## 6. Statements & Proof Endpoints

### 6.1 Get Account Statement / Ledger Entries
- **Method:** `GET`
- **URL:** `/api/v1/accounts/{accountId}/statement`
- **Query Parameters:**
  - `page`: default `0`
  - `size`: default `20`
  - `startDate`: optional ISO 8601 string
  - `endDate`: optional ISO 8601 string
- **Purpose:** Retrieve immutable chronological double-entry postings for an account.
- **Required Role:** `ROLE_USER` (Account owner) or `ROLE_AUDITOR`
- **Response:** `200 OK`
  ```json
  {
    "accountId": 501,
    "accountNumber": "LL-US-8921-0001",
    "currentBalance": "1200.50",
    "reconciliationStatus": "VERIFIED_MATCH",
    "totalPages": 1,
    "totalEntries": 2,
    "entries": [
      {
        "entryId": 901,
        "transactionReference": "TX-XFER-3849102",
        "entryType": "DEBIT",
        "amount": "350.00",
        "balanceAfter": "1200.50",
        "postedAt": "2026-11-06T15:45:00.000Z"
      },
      {
        "entryId": 850,
        "transactionReference": "TX-DEP-990214",
        "entryType": "CREDIT",
        "amount": "500.00",
        "balanceAfter": "1550.50",
        "postedAt": "2026-11-06T15:40:00.000Z"
      }
    ]
  }
  ```

---

## 7. Administrative & Auditor Endpoints

### 7.1 Admin: List All Users & Account Health
- **Method:** `GET`
- **URL:** `/api/v1/admin/users`
- **Purpose:** Administrative user overview and account state inspection.
- **Required Role:** `ROLE_ADMIN`
- **Response:** `200 OK`

### 7.2 Admin: Freeze / Unfreeze Account
- **Method:** `POST`
- **URL:** `/api/v1/admin/accounts/{accountId}/status`
- **Purpose:** Suspend or resume account transaction capabilities.
- **Required Role:** `ROLE_ADMIN`
- **Request Body:**
  ```json
  {
    "status": "FROZEN",
    "reason": "Suspected unauthorized activity investigation"
  }
  ```
- **Response:** `200 OK`

### 7.3 Auditor: Reconcile Specific Account
- **Method:** `GET`
- **URL:** `/api/v1/auditor/reconcile/{accountId}`
- **Purpose:** Mathematically prove that materialized balance equals the sum of ledger entries.
- **Required Role:** `ROLE_AUDITOR`
- **Response:** `200 OK`
  ```json
  {
    "accountId": 501,
    "accountNumber": "LL-US-8921-0001",
    "materializedBalance": "1200.50",
    "totalCreditSum": "2000.00",
    "totalDebitSum": "799.50",
    "computedLedgerBalance": "1200.50",
    "discrepancy": "0.00",
    "isReconciled": true,
    "verifiedAt": "2026-11-06T15:50:00.000Z"
  }
  ```

### 7.4 Auditor: System-Wide Balance Integrity Verification
- **Method:** `GET`
- **URL:** `/api/v1/auditor/system-reconciliation`
- **Purpose:** Validate conservation of money across entire system ($\sum \text{Debits} == \sum \text{Credits}$).
- **Required Role:** `ROLE_AUDITOR`
- **Response:** `200 OK`
  ```json
  {
    "totalSystemAccounts": 142,
    "totalLedgerTransactions": 1840,
    "totalLedgerEntries": 3680,
    "systemTotalDebits": "481900.00",
    "systemTotalCredits": "481900.00",
    "systemDelta": "0.00",
    "integrityStatus": "PASS_PERFECT_CONSERVATION",
    "evaluatedAt": "2026-11-06T15:52:00.000Z"
  }
  ```
