-- ==============================================================================
-- LedgerLine — V3 Schema Migration
-- Idempotency Records & Request Fingerprinting
-- Engine: MySQL 8.0 (InnoDB) | Timestamps: UTC (DATETIME(6))
-- ==============================================================================

CREATE TABLE IF NOT EXISTS idempotency_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    operation_type VARCHAR(30) NOT NULL DEFAULT 'TRANSFER',
    request_path VARCHAR(255) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    transaction_reference VARCHAR(64) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    response_code INT NULL,
    response_payload LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_idemp_user_op_key UNIQUE (user_id, operation_type, idempotency_key),
    CONSTRAINT fk_idemp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_idemp_key ON idempotency_records(idempotency_key);
CREATE INDEX idx_idemp_user_id ON idempotency_records(user_id);
CREATE INDEX idx_idemp_created ON idempotency_records(created_at);

