package com.ledgerline.service;

import com.ledgerline.dto.DepositRequest;
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
import com.ledgerline.entity.UserStatus;
import com.ledgerline.exception.AccountStateException;
import com.ledgerline.exception.InsufficientFundsException;
import com.ledgerline.exception.ResourceNotFoundException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.AuditEventRepository;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.repository.LedgerTransactionRepository;
import com.ledgerline.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Transaction Service Unit Tests — Deposits, Withdrawals & Ledger Invariants")
class DepositWithdrawalServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerTransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository entryRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private UserPrincipal customerUser;
    private UserPrincipal otherUser;
    private Account customerAccount;
    private Account clearingAccount;

    @BeforeEach
    void setUp() {
        customerUser = new UserPrincipal(
                1L,
                "alice",
                "alice@example.com",
                "Alice Smith",
                "encoded_pw",
                UserStatus.ACTIVE,
                List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        otherUser = new UserPrincipal(
                2L,
                "bob",
                "bob@example.com",
                "Bob Jones",
                "encoded_pw",
                UserStatus.ACTIVE,
                List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        customerAccount = new Account("LL-IN-TEST-1001", 1L, AccountType.CHECKING, "INR", new BigDecimal("1000.00"));
        customerAccount.setId(10L);

        clearingAccount = new Account("SYS_CLEARING_INR", 0L, AccountType.SYSTEM_CLEARING, "INR", BigDecimal.ZERO);
        clearingAccount.setId(99L);
    }

    @Test
    @DisplayName("Deposit creates balanced debit/credit entries, updates balances and posts transaction")
    void testDeposit_success() {
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(customerAccount));
        when(accountRepository.findByAccountNumberWithLock("SYS_CLEARING_INR"))
                .thenReturn(Optional.of(clearingAccount));
        when(transactionRepository.save(any(LedgerTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        DepositRequest request = new DepositRequest(new BigDecimal("500.00"), "Test salary deposit");
        TransactionResponse response = transactionService.deposit(10L, request, customerUser, "127.0.0.1");

        assertNotNull(response);
        assertEquals(TransactionType.DEPOSIT, response.getTransactionType());
        assertEquals(TransactionStatus.POSTED, response.getStatus());
        assertEquals(new BigDecimal("500.00"), response.getAmount());
        assertEquals(new BigDecimal("1500.00"), customerAccount.getBalance());
        assertEquals(new BigDecimal("-500.00"), clearingAccount.getBalance());

        // Verify transaction was saved with exactly two balanced entries
        ArgumentCaptor<LedgerTransaction> txCaptor = ArgumentCaptor.forClass(LedgerTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        LedgerTransaction savedTx = txCaptor.getValue();

        List<LedgerEntry> entries = savedTx.getEntries();
        assertEquals(2, entries.size(), "Deposit must produce exactly 2 entries");

        BigDecimal totalDebits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, totalDebits.compareTo(totalCredits), "Total debits must equal total credits");
        assertEquals(0, new BigDecimal("500.00").compareTo(totalDebits));
    }

    @Test
    @DisplayName("Deposit on non-existent account throws ResourceNotFoundException")
    void testDeposit_nonExistentAccount() {
        when(accountRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

        DepositRequest request = new DepositRequest(new BigDecimal("100.00"), "Deposit");

        assertThrows(ResourceNotFoundException.class, () ->
                transactionService.deposit(999L, request, customerUser, "127.0.0.1")
        );
    }

    @Test
    @DisplayName("Deposit on frozen/suspended account throws AccountStateException")
    void testDeposit_suspendedAccount() {
        customerAccount.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(customerAccount));

        DepositRequest request = new DepositRequest(new BigDecimal("100.00"), "Deposit");

        assertThrows(AccountStateException.class, () ->
                transactionService.deposit(10L, request, customerUser, "127.0.0.1")
        );
    }

    @Test
    @DisplayName("Withdrawal creates balanced debit/credit entries and decreases balance")
    void testWithdrawal_success() {
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(customerAccount));
        when(accountRepository.findByAccountNumberWithLock("SYS_CLEARING_INR"))
                .thenReturn(Optional.of(clearingAccount));
        when(transactionRepository.save(any(LedgerTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        WithdrawalRequest request = new WithdrawalRequest(new BigDecimal("400.00"), "ATM withdrawal");
        TransactionResponse response = transactionService.withdraw(10L, request, customerUser, "127.0.0.1");

        assertNotNull(response);
        assertEquals(TransactionType.WITHDRAWAL, response.getTransactionType());
        assertEquals(TransactionStatus.POSTED, response.getStatus());
        assertEquals(new BigDecimal("400.00"), response.getAmount());
        assertEquals(new BigDecimal("600.00"), customerAccount.getBalance());
        assertEquals(new BigDecimal("400.00"), clearingAccount.getBalance());

        // Verify transaction debits customer and credits clearing
        ArgumentCaptor<LedgerTransaction> txCaptor = ArgumentCaptor.forClass(LedgerTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        LedgerTransaction savedTx = txCaptor.getValue();

        List<LedgerEntry> entries = savedTx.getEntries();
        assertEquals(2, entries.size());

        BigDecimal totalDebits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, totalDebits.compareTo(totalCredits));
        assertEquals(0, new BigDecimal("400.00").compareTo(totalDebits));
    }

    @Test
    @DisplayName("Withdrawal exceeding balance throws InsufficientFundsException and records AuditEvent")
    void testWithdrawal_insufficientFunds_recordsAuditEvent() {
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(customerAccount));

        WithdrawalRequest request = new WithdrawalRequest(new BigDecimal("1500.00"), "Overdraft attempt");

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class, () ->
                transactionService.withdraw(10L, request, customerUser, "127.0.0.1")
        );

        assertEquals(new BigDecimal("1500.00"), ex.getAttemptedAmount());
        assertEquals(new BigDecimal("1000.00"), ex.getAvailableBalance());
        assertEquals(new BigDecimal("1000.00"), customerAccount.getBalance(), "Balance must not change on failure");

        // Verify audit event was recorded
        verify(auditService).recordEvent(
                eq("WITHDRAWAL_REFUSED_INSUFFICIENT_FUNDS"),
                eq(1L),
                eq(10L),
                any(),
                any()
        );

        // Verify transaction repository was never written to
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Unauthorized user cannot withdraw from another user's account")
    void testWithdrawal_unauthorizedUser_throwsAccessDenied() {
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(customerAccount));

        WithdrawalRequest request = new WithdrawalRequest(new BigDecimal("100.00"), "Attempt");

        assertThrows(AccessDeniedException.class, () ->
                transactionService.withdraw(10L, request, otherUser, "127.0.0.1")
        );

        verify(transactionRepository, never()).save(any());
    }
}
