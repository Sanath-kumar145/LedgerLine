package com.ledgerline.service;

import com.ledgerline.dto.AccountStatementResponse;
import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.LedgerEntryDto;
import com.ledgerline.dto.TransactionResponse;
import com.ledgerline.dto.WithdrawalRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountStatus;
import com.ledgerline.entity.AccountType;
import com.ledgerline.entity.AuditEvent;
import com.ledgerline.entity.EntryType;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.LedgerTransaction;
import com.ledgerline.entity.TransactionStatus;
import com.ledgerline.entity.TransactionType;
import com.ledgerline.exception.AccountStateException;
import com.ledgerline.exception.InsufficientFundsException;
import com.ledgerline.exception.InvalidTransactionException;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;
    private final AuditService auditService;

    public TransactionServiceImpl(AccountRepository accountRepository,
                                  LedgerTransactionRepository transactionRepository,
                                  LedgerEntryRepository entryRepository,
                                  AuditService auditService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public TransactionResponse deposit(Long accountId, DepositRequest request, UserPrincipal currentUser, String ipAddress) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required.");
        }

        validateAmount(request.getAmount(), "Deposit");
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.UNNECESSARY);

        // 1. Lock customer account row via SELECT ... FOR UPDATE (Fresh Read Principle)
        Account lockedCustomerAccount = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));

        validateAccountOwnershipOrAdmin(lockedCustomerAccount, currentUser);

        if (lockedCustomerAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountStateException("Account is not active (status: " + lockedCustomerAccount.getStatus() + "). Deposits cannot be accepted.");
        }

        String currency = lockedCustomerAccount.getCurrency() != null ? lockedCustomerAccount.getCurrency() : "INR";

        // 2. Lock system clearing counterparty account row
        Account lockedClearingAccount = getOrInitClearingAccountWithLock(currency);

        // 3. Atomically update balances
        BigDecimal customerNewBalance = lockedCustomerAccount.getBalance().add(amount);
        BigDecimal clearingNewBalance = lockedClearingAccount.getBalance().subtract(amount);

        lockedCustomerAccount.setBalance(customerNewBalance);
        lockedClearingAccount.setBalance(clearingNewBalance);

        accountRepository.save(lockedCustomerAccount);
        accountRepository.save(lockedClearingAccount);

        // 4. Create LedgerTransaction journal header
        LedgerTransaction transaction = new LedgerTransaction(
                currentUser.getId(),
                TransactionType.DEPOSIT,
                amount,
                currency,
                request.getDescription() != null ? request.getDescription() : "External deposit into " + lockedCustomerAccount.getAccountNumber()
        );
        transaction.markCompleted();

        // 5. Post atomic balanced double entries:
        // Debit: Clearing Account (External asset inflow)
        // Credit: Customer Account (Customer balance increase)
        LedgerEntry debitEntry = new LedgerEntry(lockedClearingAccount, EntryType.DEBIT, amount, clearingNewBalance);
        LedgerEntry creditEntry = new LedgerEntry(lockedCustomerAccount, EntryType.CREDIT, amount, customerNewBalance);

        transaction.addEntry(debitEntry);
        transaction.addEntry(creditEntry);

        LedgerTransaction savedTx = transactionRepository.save(transaction);

        // 6. Record audit event
        auditService.recordEvent(
                "DEPOSIT_COMPLETED",
                currentUser.getId(),
                lockedCustomerAccount.getId(),
                "Deposited " + amount + " " + currency + " into " + lockedCustomerAccount.getAccountNumber() + "; new balance: " + customerNewBalance,
                ipAddress
        );

        log.info("Deposit successful: txRef={} accountId={} amount={} {} newBalance={}",
                savedTx.getTransactionReference(), lockedCustomerAccount.getId(), amount, currency, customerNewBalance);

        return new TransactionResponse(
                savedTx.getTransactionReference(),
                lockedCustomerAccount.getId(),
                lockedCustomerAccount.getAccountNumber(),
                TransactionType.DEPOSIT,
                amount,
                currency,
                customerNewBalance,
                TransactionStatus.POSTED,
                savedTx.getDescription(),
                savedTx.getCompletedAt()
        );
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public TransactionResponse withdraw(Long accountId, WithdrawalRequest request, UserPrincipal currentUser, String ipAddress) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required.");
        }

        validateAmount(request.getAmount(), "Withdrawal");
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.UNNECESSARY);

        // 1. Lock customer account row via SELECT ... FOR UPDATE (Fresh Read Principle)
        Account lockedCustomerAccount = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));

        validateAccountOwnershipOrAdmin(lockedCustomerAccount, currentUser);

        if (lockedCustomerAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountStateException("Account is not active (status: " + lockedCustomerAccount.getStatus() + "). Withdrawals cannot be processed.");
        }

        // 2. Authoritative in-transaction balance check under exclusive row lock
        if (lockedCustomerAccount.getBalance().compareTo(amount) < 0) {
            log.warn("Withdrawal refused for account {}: requested={}, available={}",
                    lockedCustomerAccount.getAccountNumber(), amount, lockedCustomerAccount.getBalance());

            auditService.recordEvent(
                    "WITHDRAWAL_REFUSED_INSUFFICIENT_FUNDS",
                    currentUser.getId(),
                    lockedCustomerAccount.getId(),
                    "Attempted withdrawal of " + amount + " exceeded available balance " + lockedCustomerAccount.getBalance(),
                    ipAddress
            );

            throw new InsufficientFundsException(amount, lockedCustomerAccount.getBalance());
        }

        String currency = lockedCustomerAccount.getCurrency() != null ? lockedCustomerAccount.getCurrency() : "INR";

        // 3. Lock system clearing counterparty account row
        Account lockedClearingAccount = getOrInitClearingAccountWithLock(currency);

        // 4. Atomically update balances
        BigDecimal customerNewBalance = lockedCustomerAccount.getBalance().subtract(amount);
        BigDecimal clearingNewBalance = lockedClearingAccount.getBalance().add(amount);

        lockedCustomerAccount.setBalance(customerNewBalance);
        lockedClearingAccount.setBalance(clearingNewBalance);

        accountRepository.save(lockedCustomerAccount);
        accountRepository.save(lockedClearingAccount);

        // 5. Create LedgerTransaction journal header
        LedgerTransaction transaction = new LedgerTransaction(
                currentUser.getId(),
                TransactionType.WITHDRAWAL,
                amount,
                currency,
                request.getDescription() != null ? request.getDescription() : "External withdrawal from " + lockedCustomerAccount.getAccountNumber()
        );
        transaction.markCompleted();

        // 6. Post atomic balanced double entries:
        // Debit: Customer Account (Customer balance decrease)
        // Credit: Clearing Account (External asset outflow)
        LedgerEntry debitEntry = new LedgerEntry(lockedCustomerAccount, EntryType.DEBIT, amount, customerNewBalance);
        LedgerEntry creditEntry = new LedgerEntry(lockedClearingAccount, EntryType.CREDIT, amount, clearingNewBalance);

        transaction.addEntry(debitEntry);
        transaction.addEntry(creditEntry);

        LedgerTransaction savedTx = transactionRepository.save(transaction);

        // 7. Record audit event
        auditService.recordEvent(
                "WITHDRAWAL_COMPLETED",
                currentUser.getId(),
                lockedCustomerAccount.getId(),
                "Withdrew " + amount + " " + currency + " from " + lockedCustomerAccount.getAccountNumber() + "; new balance: " + customerNewBalance,
                ipAddress
        );

        log.info("Withdrawal successful: txRef={} accountId={} amount={} {} newBalance={}",
                savedTx.getTransactionReference(), lockedCustomerAccount.getId(), amount, currency, customerNewBalance);

        return new TransactionResponse(
                savedTx.getTransactionReference(),
                lockedCustomerAccount.getId(),
                lockedCustomerAccount.getAccountNumber(),
                TransactionType.WITHDRAWAL,
                amount,
                currency,
                customerNewBalance,
                TransactionStatus.POSTED,
                savedTx.getDescription(),
                savedTx.getCompletedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AccountStatementResponse getAccountStatement(Long accountId, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required.");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));

        validateAccountOwnershipOrAuditor(account, currentUser);

        List<LedgerEntry> entries = entryRepository.findByAccountIdOrderByPostedAtDesc(accountId);

        List<LedgerEntryDto> entryDtos = entries.stream()
                .map(e -> new LedgerEntryDto(
                        e.getId(),
                        e.getTransaction().getTransactionReference(),
                        e.getEntryType(),
                        e.getAmount(),
                        e.getBalanceAfter(),
                        e.getPostedAt(),
                        e.getTransaction().getDescription()
                ))
                .collect(Collectors.toList());

        return new AccountStatementResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCurrency(),
                account.getBalance(),
                entryDtos.size(),
                entryDtos
        );
    }

    private void validateAmount(BigDecimal amount, String operation) {
        if (amount == null) {
            throw new InvalidTransactionException(operation + " amount must be provided.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException(operation + " amount must be strictly greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new InvalidTransactionException(operation + " amount cannot have more than two decimal places.");
        }
    }

    private void validateAccountOwnershipOrAdmin(Account account, UserPrincipal currentUser) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !account.isOwnedBy(currentUser.getId())) {
            log.warn("Security Alert: User id={} attempted unauthorized transaction on account id={} owned by user id={}",
                    currentUser.getId(), account.getId(), account.getUserId());
            throw new AccessDeniedException("Access denied: You do not own this account.");
        }
    }

    private void validateAccountOwnershipOrAuditor(Account account, UserPrincipal currentUser) {
        boolean isStaff = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_AUDITOR"));

        if (!isStaff && !account.isOwnedBy(currentUser.getId())) {
            log.warn("Security Alert: User id={} attempted unauthorized statement inspection on account id={} owned by user id={}",
                    currentUser.getId(), account.getId(), account.getUserId());
            throw new AccessDeniedException("Access denied: You do not own this account.");
        }
    }

    private Account getOrInitClearingAccountWithLock(String currency) {
        String clearingNumber = "SYS_CLEARING_" + currency;
        return accountRepository.findByAccountNumberWithLock(clearingNumber)
                .orElseGet(() -> accountRepository.findByAccountTypeAndCurrencyWithLock(AccountType.SYSTEM_CLEARING, currency)
                        .orElseGet(() -> {
                            Account newClearing = new Account(
                                    clearingNumber,
                                    null,
                                    AccountType.SYSTEM_CLEARING,
                                    currency,
                                    BigDecimal.ZERO.setScale(2)
                            );
                            newClearing.setStatus(AccountStatus.ACTIVE);
                            return accountRepository.save(newClearing);
                        }));
    }
}
