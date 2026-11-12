package com.ledgerline.repository;

import com.ledgerline.entity.EntryType;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByAccountIdOrderByPostedAtDesc(Long accountId);

    List<LedgerEntry> findByTransactionId(Long transactionId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e " +
           "WHERE e.account.id = :accountId AND e.entryType = :entryType AND e.transaction.status = :status")
    BigDecimal sumAmountByAccountIdAndEntryTypeAndStatus(
            @Param("accountId") Long accountId,
            @Param("entryType") EntryType entryType,
            @Param("status") TransactionStatus status);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e " +
           "WHERE e.entryType = :entryType AND e.transaction.status = :status")
    BigDecimal sumTotalAmountByEntryTypeAndStatus(
            @Param("entryType") EntryType entryType,
            @Param("status") TransactionStatus status);

    @Override
    default void delete(LedgerEntry entity) {
        throw new UnsupportedOperationException("LedgerLine Integrity Violation: Ledger entries are immutable and cannot be deleted.");
    }

    @Override
    default void deleteById(Long id) {
        throw new UnsupportedOperationException("LedgerLine Integrity Violation: Ledger entries are immutable and cannot be deleted.");
    }

    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException("LedgerLine Integrity Violation: Ledger entries are immutable and cannot be deleted.");
    }

    @Override
    default void deleteAll(Iterable<? extends LedgerEntry> entities) {
        throw new UnsupportedOperationException("LedgerLine Integrity Violation: Ledger entries are immutable and cannot be deleted.");
    }

    @Override
    default void deleteAllById(Iterable<? extends Long> ids) {
        throw new UnsupportedOperationException("LedgerLine Integrity Violation: Ledger entries are immutable and cannot be deleted.");
    }
}
