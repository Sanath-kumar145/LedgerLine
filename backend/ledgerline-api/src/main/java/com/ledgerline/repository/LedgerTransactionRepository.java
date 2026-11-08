package com.ledgerline.repository;

import com.ledgerline.entity.LedgerTransaction;
import com.ledgerline.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {
    Optional<LedgerTransaction> findByTransactionReference(String transactionReference);
    List<LedgerTransaction> findByInitiatedByUserId(Long initiatedByUserId);
    Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey);
    List<LedgerTransaction> findByStatus(TransactionStatus status);

    @Query("SELECT t.id FROM LedgerTransaction t JOIN t.entries e WHERE t.status = :status GROUP BY t.id " +
           "HAVING SUM(CASE WHEN e.entryType = com.ledgerline.entity.EntryType.DEBIT THEN e.amount ELSE 0 END) != " +
           "SUM(CASE WHEN e.entryType = com.ledgerline.entity.EntryType.CREDIT THEN e.amount ELSE 0 END)")
    List<Long> findUnbalancedTransactionIdsByStatus(@Param("status") TransactionStatus status);
}

