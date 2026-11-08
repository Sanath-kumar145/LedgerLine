-- ==============================================================================
-- LedgerLine — V2 Schema Migration
-- Double-Entry Ledger Core: Transactions, Entries, Audit Events, and System Clearing
-- Engine: MySQL 8.0 (InnoDB) | Timestamps: UTC (DATETIME(6))
-- ==============================================================================

-- 1. Ledger Transactions (Transaction Journal Headers)
CREATE TABLE IF NOT EXISTS ledger_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_reference VARCHAR(64) NOT NULL,
    initiated_by_user_id BIGINT NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    description VARCHAR(255) NULL,
    idempotency_key VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    CONSTRAINT uk_ledger_transactions_ref UNIQUE (transaction_reference),
    CONSTRAINT chk_ledger_tx_amount CHECK (total_amount > 0.00),
    CONSTRAINT fk_ledger_tx_initiator FOREIGN KEY (initiated_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ledger_tx_initiator ON ledger_transactions(initiated_by_user_id);
CREATE INDEX idx_ledger_tx_created ON ledger_transactions(created_at);
CREATE INDEX idx_ledger_tx_idemp ON ledger_transactions(idempotency_key);
CREATE INDEX idx_ledger_tx_status ON ledger_transactions(status);

-- 2. Ledger Entries (Atomic Double-Entry Postings)
CREATE TABLE IF NOT EXISTS ledger_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    balance_after DECIMAL(19, 2) NOT NULL,
    posted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_ledger_entries_amount CHECK (amount > 0.00),
    CONSTRAINT chk_ledger_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT fk_entries_tx FOREIGN KEY (transaction_id) REFERENCES ledger_transactions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_entries_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_entries_account_posted ON ledger_entries(account_id, posted_at);
CREATE INDEX idx_entries_tx_id ON ledger_entries(transaction_id);

-- 3. Audit Events (Security & Refused Attempt Logging — Autonomous Immutable Audit Log)
CREATE TABLE IF NOT EXISTS audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    user_id BIGINT NULL,
    account_id BIGINT NULL,
    details VARCHAR(1000) NULL,
    ip_address VARCHAR(45) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_audit_event_type ON audit_events(event_type);
CREATE INDEX idx_audit_user_id ON audit_events(user_id);
CREATE INDEX idx_audit_account_id ON audit_events(account_id);
CREATE INDEX idx_audit_created ON audit_events(created_at);

-- 4. Accounts Table Auxiliary Index for Clearing Lookup
CREATE INDEX idx_accounts_type_curr ON accounts(account_type, currency);

-- 5. Seed System Clearing Accounts (INR & USD)
INSERT INTO accounts (account_number, user_id, account_type, currency, balance, status, version)
VALUES
    ('SYS_CLEARING_INR', NULL, 'SYSTEM_CLEARING', 'INR', 0.00, 'ACTIVE', 0),
    ('SYS_CLEARING_USD', NULL, 'SYSTEM_CLEARING', 'USD', 0.00, 'ACTIVE', 0)
ON DUPLICATE KEY UPDATE account_number = account_number;
