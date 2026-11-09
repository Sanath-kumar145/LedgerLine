package com.ledgerline.service;

import com.ledgerline.entity.AuditEvent;

/**
 * Service for recording financial and system audit events.
 *
 * Implements a Dual Transaction Boundary Architecture:
 * 1. Consistent Audit Events (Propagation.REQUIRED):
 *    State-change completion records (e.g. TRANSFER_COMPLETED, DEPOSIT_COMPLETED, WITHDRAWAL_COMPLETED)
 *    must commit or roll back atomically with the underlying financial state mutations
 *    (account balances, ledger transactions, double entries, and idempotency records).
 *    If the outer business transaction rolls back, consistent audit events roll back with it,
 *    preventing false audit records.
 *
 * 2. Autonomous Audit Events (Propagation.REQUIRES_NEW):
 *    Security violations, fraud alerts, discrepancy detections, and refusal attempts
 *    (e.g. TRANSFER_REFUSED_INSUFFICIENT_FUNDS) must commit in an independent autonomous transaction.
 *    Even when the outer transaction rolls back due to an exception (e.g. InsufficientFundsException),
 *    the autonomous audit log is safely and permanently persisted.
 */
public interface AuditService {

    /**
     * Records a transactionally consistent audit event that participates in the active
     * database transaction (Propagation.REQUIRED).
     * If the enclosing transaction rolls back, this audit record rolls back atomically.
     */
    AuditEvent recordConsistentEvent(String eventType, Long userId, Long accountId, String details, String ipAddress);

    /**
     * Records an autonomous audit event in an independent transaction (Propagation.REQUIRES_NEW).
     * Suspends any current transaction and commits immediately, ensuring critical audit events
     * (e.g., security refusals, fraud checks, discrepancy alerts) are permanently recorded
     * even if the calling business transaction rolls back.
     */
    AuditEvent recordAutonomousEvent(String eventType, Long userId, Long accountId, String details, String ipAddress);

    /**
     * Default audit event recorder. Uses transactionally consistent propagation (Propagation.REQUIRED).
     */
    AuditEvent recordEvent(String eventType, Long userId, Long accountId, String details, String ipAddress);
}
