package com.ledgerline.service;

import com.ledgerline.dto.AccountReconciliationResponse;
import com.ledgerline.dto.SystemReconciliationResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountType;
import com.ledgerline.entity.AuditEvent;
import com.ledgerline.entity.EntryType;
import com.ledgerline.entity.TransactionStatus;
import com.ledgerline.exception.ResourceNotFoundException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.AuditEventRepository;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.repository.LedgerTransactionRepository;
import com.ledgerline.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationServiceImpl.class);

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;
    private final AuditService auditService;

    public ReconciliationServiceImpl(AccountRepository accountRepository,
                                      LedgerTransactionRepository transactionRepository,
                                      LedgerEntryRepository entryRepository,
                                      AuditService auditService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountReconciliationResponse reconcileAccount(Long accountId, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required.");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));

        boolean isStaff = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_AUDITOR"));

        if (!isStaff && !account.isOwnedBy(currentUser.getId())) {
            log.warn("Security Alert: User id={} attempted unauthorized reconciliation query on account id={}",
                    currentUser.getId(), accountId);
            throw new AccessDeniedException("Access denied: You do not own this account.");
        }

        BigDecimal totalCredits = entryRepository.sumAmountByAccountIdAndEntryTypeAndStatus(
                accountId, EntryType.CREDIT, TransactionStatus.POSTED);
        BigDecimal totalDebits = entryRepository.sumAmountByAccountIdAndEntryTypeAndStatus(
                accountId, EntryType.DEBIT, TransactionStatus.POSTED);

        BigDecimal computedLedgerBalance;
        if (account.getAccountType() == AccountType.SYSTEM_CLEARING) {
            computedLedgerBalance = totalDebits.subtract(totalCredits);
        } else {
            computedLedgerBalance = totalCredits.subtract(totalDebits);
        }

        BigDecimal materializedBalance = account.getBalance();
        BigDecimal discrepancy = materializedBalance.subtract(computedLedgerBalance).abs();
        boolean isReconciled = discrepancy.compareTo(BigDecimal.ZERO) == 0;

        if (!isReconciled) {
            log.error("CRITICAL INTEGRITY DISCREPANCY on account id={} {}: materialized={}, computed={}, delta={}",
                    account.getId(), account.getAccountNumber(), materializedBalance, computedLedgerBalance, discrepancy);

            auditService.recordEvent(
                    "RECONCILIATION_DISCREPANCY",
                    currentUser.getId(),
                    account.getId(),
                    "Discrepancy detected: materialized=" + materializedBalance + ", computed=" + computedLedgerBalance + ", delta=" + discrepancy,
                    null
            );
        }

        return new AccountReconciliationResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCurrency(),
                materializedBalance,
                totalCredits,
                totalDebits,
                computedLedgerBalance,
                discrepancy,
                isReconciled,
                Instant.now()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public SystemReconciliationResponse reconcileSystem(UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required.");
        }

        boolean isStaff = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_AUDITOR"));

        if (!isStaff) {
            throw new AccessDeniedException("Access denied: System reconciliation requires ROLE_ADMIN or ROLE_AUDITOR.");
        }

        long totalAccounts = accountRepository.count();
        long totalTransactions = transactionRepository.count();
        long totalEntries = entryRepository.count();

        BigDecimal systemTotalDebits = entryRepository.sumTotalAmountByEntryTypeAndStatus(
                EntryType.DEBIT, TransactionStatus.POSTED);
        BigDecimal systemTotalCredits = entryRepository.sumTotalAmountByEntryTypeAndStatus(
                EntryType.CREDIT, TransactionStatus.POSTED);

        BigDecimal systemDelta = systemTotalDebits.subtract(systemTotalCredits).abs();
        List<Long> unbalancedTxIds = transactionRepository.findUnbalancedTransactionIdsByStatus(TransactionStatus.POSTED);
        int unbalancedCount = unbalancedTxIds.size();

        String status = (systemDelta.compareTo(BigDecimal.ZERO) == 0 && unbalancedCount == 0)
                ? "SYSTEM_BALANCED"
                : "SYSTEM_CORRUPTED";

        if (!"SYSTEM_BALANCED".equals(status)) {
            log.error("SYSTEM CONSERVATION FAILURE: debits={}, credits={}, delta={}, unbalancedTxCount={}",
                    systemTotalDebits, systemTotalCredits, systemDelta, unbalancedCount);
        }

        return new SystemReconciliationResponse(
                totalAccounts,
                totalTransactions,
                totalEntries,
                systemTotalDebits,
                systemTotalCredits,
                systemDelta,
                unbalancedCount,
                status,
                Instant.now()
        );
    }
}
