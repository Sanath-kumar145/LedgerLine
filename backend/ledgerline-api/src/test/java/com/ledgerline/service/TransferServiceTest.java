package com.ledgerline.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.TransferResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountStatus;
import com.ledgerline.entity.AccountType;
import com.ledgerline.entity.EntryType;
import com.ledgerline.entity.IdempotencyRecord;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.LedgerTransaction;
import com.ledgerline.entity.TransactionStatus;
import com.ledgerline.entity.TransactionType;
import com.ledgerline.entity.UserStatus;
import com.ledgerline.exception.AccountStateException;
import com.ledgerline.exception.InsufficientFundsException;
import com.ledgerline.exception.InvalidTransactionException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.IdempotencyRecordRepository;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.repository.LedgerTransactionRepository;
import com.ledgerline.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Transfer Service Unit Tests — Internal Transfers & Business Rules")
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerTransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository entryRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ObjectMapper objectMapper;
    private TransferServiceImpl transferService;

    private UserPrincipal alicePrincipal;
    private UserPrincipal bobPrincipal;
    private UserPrincipal auditorPrincipal;

    private Account sourceAccount;
    private Account destinationAccount;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        transferService = new TransferServiceImpl(
                accountRepository,
                transactionRepository,
                entryRepository,
                idempotencyRecordRepository,
                auditService,
                objectMapper,
                transactionManager
        );

        alicePrincipal = new UserPrincipal(
                1L, "alice", "alice@example.com", "Alice Smith", "hash", UserStatus.ACTIVE,
                List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        bobPrincipal = new UserPrincipal(
                2L, "bob", "bob@example.com", "Bob Jones", "hash", UserStatus.ACTIVE,
                List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        auditorPrincipal = new UserPrincipal(
                3L, "auditor", "auditor@example.com", "Auditor General", "hash", UserStatus.ACTIVE,
                List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"))
        );

        sourceAccount = new Account("LL-IN-SRC-1001", 1L, AccountType.CHECKING, "INR", new BigDecimal("5000.00"));
        setAccountId(sourceAccount, 10L);

        destinationAccount = new Account("LL-IN-DST-2002", 2L, AccountType.SAVINGS, "INR", new BigDecimal("2000.00"));
        setAccountId(destinationAccount, 20L);
    }

    private void setAccountId(Account account, Long id) {
        try {
            var field = Account.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Successful transfer: Account A (₹5000) -> Account B (₹2000) for ₹1000 updates balances to ₹4000 and ₹3000")
    void testSuccessfulTransfer() {
        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-001"))
                .thenReturn(Optional.empty());

        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(destinationAccount));

        when(transactionRepository.save(any(LedgerTransaction.class))).thenAnswer(invocation -> {
            LedgerTransaction tx = invocation.getArgument(0);
            return tx;
        });

        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("1000.00"), "Monthly rent share");
        TransferResponse response = transferService.transfer(request, "key-001", alicePrincipal, "127.0.0.1");

        assertNotNull(response);
        assertEquals(new BigDecimal("1000.00"), response.getAmount());
        assertEquals("INR", response.getCurrency());
        assertEquals(new BigDecimal("4000.00"), response.getSourceBalanceAfter());
        assertEquals(new BigDecimal("3000.00"), response.getDestinationBalanceAfter());
        assertEquals(com.ledgerline.entity.TransactionStatus.POSTED, response.getStatus());
        assertFalse(response.isReplayed());

        // Check account entity balance states
        assertEquals(new BigDecimal("4000.00"), sourceAccount.getBalance());
        assertEquals(new BigDecimal("3000.00"), destinationAccount.getBalance());

        // Verify ledger entries
        ArgumentCaptor<LedgerTransaction> txCaptor = ArgumentCaptor.forClass(LedgerTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        LedgerTransaction savedTx = txCaptor.getValue();
        assertEquals(TransactionType.TRANSFER, savedTx.getTransactionType());
        assertEquals(2, savedTx.getEntries().size());

        LedgerEntry debitEntry = savedTx.getEntries().stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT).findFirst().orElseThrow();
        LedgerEntry creditEntry = savedTx.getEntries().stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT).findFirst().orElseThrow();

        assertEquals(sourceAccount, debitEntry.getAccount());
        assertEquals(new BigDecimal("1000.00"), debitEntry.getAmount());
        assertEquals(new BigDecimal("4000.00"), debitEntry.getBalanceAfter());

        assertEquals(destinationAccount, creditEntry.getAccount());
        assertEquals(new BigDecimal("1000.00"), creditEntry.getAmount());
        assertEquals(new BigDecimal("3000.00"), creditEntry.getBalanceAfter());

        // Verify idempotency record saved
        verify(idempotencyRecordRepository).save(any(IdempotencyRecord.class));

        // Verify audit event is transactionally consistent
        verify(auditService).recordConsistentEvent(eq("TRANSFER_COMPLETED"), eq(1L), eq(10L), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Reject transfer when transfer amount is zero")
    void testRejectZeroAmount() {
        TransferRequest request = new TransferRequest(10L, 20L, BigDecimal.ZERO, "Zero transfer");

        assertThrows(InvalidTransactionException.class, () ->
                transferService.transfer(request, "key-002", alicePrincipal, "127.0.0.1"));

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reject transfer when transfer amount is negative")
    void testRejectNegativeAmount() {
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("-100.00"), "Negative transfer");

        assertThrows(InvalidTransactionException.class, () ->
                transferService.transfer(request, "key-003", alicePrincipal, "127.0.0.1"));

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reject transfer when source and destination are the same account")
    void testRejectSameAccountTransfer() {
        TransferRequest request = new TransferRequest(10L, 10L, new BigDecimal("100.00"), "Self transfer");

        assertThrows(InvalidTransactionException.class, () ->
                transferService.transfer(request, "key-004", alicePrincipal, "127.0.0.1"));

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reject transfer when source account has insufficient funds")
    void testRejectInsufficientFunds() {
        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-005"))
                .thenReturn(Optional.empty());

        sourceAccount.setBalance(new BigDecimal("500.00"));
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(destinationAccount));

        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("600.00"), "Overdraft attempt");

        assertThrows(InsufficientFundsException.class, () ->
                transferService.transfer(request, "key-005", alicePrincipal, "127.0.0.1"));

        // Source balance unchanged
        assertEquals(new BigDecimal("500.00"), sourceAccount.getBalance());
        verify(transactionRepository, never()).save(any());

        // Verify autonomous refusal audit log recorded
        verify(auditService).recordAutonomousEvent(eq("TRANSFER_REFUSED_INSUFFICIENT_FUNDS"), eq(1L), eq(10L), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Reject transfer when source account is not active")
    void testRejectInactiveSourceAccount() {
        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-006"))
                .thenReturn(Optional.empty());

        sourceAccount.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(destinationAccount));

        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("100.00"), "Frozen source");

        assertThrows(AccountStateException.class, () ->
                transferService.transfer(request, "key-006", alicePrincipal, "127.0.0.1"));
    }

    @Test
    @DisplayName("Reject transfer when destination account is not active")
    void testRejectInactiveDestinationAccount() {
        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-007"))
                .thenReturn(Optional.empty());

        destinationAccount.setStatus(AccountStatus.CLOSED);
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(destinationAccount));

        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("100.00"), "Closed destination");

        assertThrows(AccountStateException.class, () ->
                transferService.transfer(request, "key-007", alicePrincipal, "127.0.0.1"));
    }

    @Test
    @DisplayName("Reject transfer when currencies do not match")
    void testRejectCurrencyMismatch() {
        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-008"))
                .thenReturn(Optional.empty());

        Account usdAccount = new Account("LL-US-DST-3003", 2L, AccountType.CHECKING, "USD", new BigDecimal("100.00"));
        setAccountId(usdAccount, 30L);

        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdWithLock(30L)).thenReturn(Optional.of(usdAccount));

        TransferRequest request = new TransferRequest(10L, 30L, new BigDecimal("100.00"), "Cross-currency attempt");

        assertThrows(InvalidTransactionException.class, () ->
                transferService.transfer(request, "key-008", alicePrincipal, "127.0.0.1"));
    }

    @Test
    @DisplayName("Reject transfer when caller does not own the source account")
    void testRejectUnauthorizedSourceAccount() {
        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(2L, "TRANSFER", "key-009"))
                .thenReturn(Optional.empty());

        // Bob (userId=2) tries to transfer from Alice's account (userId=1)
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(destinationAccount));

        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("100.00"), "IDOR debit attempt");

        assertThrows(AccessDeniedException.class, () ->
                transferService.transfer(request, "key-009", bobPrincipal, "127.0.0.1"));
    }

    @Test
    @DisplayName("Reject transfer when source account is a system clearing account")
    void testRejectSourceIsSystemClearing() {
        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-010"))
                .thenReturn(Optional.empty());

        Account clearing = new Account("SYS_CLEARING_INR", 1L, AccountType.SYSTEM_CLEARING, "INR", new BigDecimal("1000.00"));
        setAccountId(clearing, 5L);

        when(accountRepository.findByIdWithLock(5L)).thenReturn(Optional.of(clearing));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(destinationAccount));

        TransferRequest request = new TransferRequest(5L, 20L, new BigDecimal("100.00"), "Clearing transfer");

        assertThrows(InvalidTransactionException.class, () ->
                transferService.transfer(request, "key-010", alicePrincipal, "127.0.0.1"));
    }

    @Test
    @DisplayName("Reject transfer when destination account is a system clearing account")
    void testRejectDestinationIsSystemClearing() {
        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-011"))
                .thenReturn(Optional.empty());

        Account clearing = new Account("SYS_CLEARING_INR", null, AccountType.SYSTEM_CLEARING, "INR", new BigDecimal("1000.00"));
        setAccountId(clearing, 50L);

        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdWithLock(50L)).thenReturn(Optional.of(clearing));

        TransferRequest request = new TransferRequest(10L, 50L, new BigDecimal("100.00"), "Clearing destination");

        assertThrows(InvalidTransactionException.class, () ->
                transferService.transfer(request, "key-011", alicePrincipal, "127.0.0.1"));
    }

    @Test
    @DisplayName("Reject transfer when initiator has ROLE_AUDITOR")
    void testRejectAuditorInitiation() {
        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(3L, "TRANSFER", "key-012"))
                .thenReturn(Optional.empty());

        Account auditorAccount = new Account("LL-IN-AUD-4004", 3L, AccountType.CHECKING, "INR", new BigDecimal("1000.00"));
        setAccountId(auditorAccount, 40L);

        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(destinationAccount));
        when(accountRepository.findByIdWithLock(40L)).thenReturn(Optional.of(auditorAccount));

        TransferRequest request = new TransferRequest(40L, 20L, new BigDecimal("100.00"), "Auditor transfer");

        assertThrows(AccessDeniedException.class, () ->
                transferService.transfer(request, "key-012", auditorPrincipal, "127.0.0.1"));
    }
}
