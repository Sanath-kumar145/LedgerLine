package com.ledgerline.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.TransferResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountType;
import com.ledgerline.entity.IdempotencyRecord;
import com.ledgerline.entity.LedgerTransaction;
import com.ledgerline.entity.TransactionStatus;
import com.ledgerline.entity.TransactionType;
import com.ledgerline.entity.UserStatus;
import com.ledgerline.exception.IdempotencyConflictException;
import com.ledgerline.exception.InsufficientFundsException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.IdempotencyRecordRepository;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.repository.LedgerTransactionRepository;
import com.ledgerline.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Transfer Idempotency Unit Tests — Key Lifecycle, Replay & Conflict Detection")
class TransferIdempotencyTest {

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
    @DisplayName("Case A: First request with new key executes transfer exactly once and persists idempotency record")
    void testFirstRequestExecutesOnce() {
        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-first"))
                .thenReturn(Optional.empty());
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(destinationAccount));
        when(transactionRepository.save(any(LedgerTransaction.class))).thenAnswer(i -> i.getArgument(0));

        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("500.00"), "First execution");
        TransferResponse response = transferService.transfer(request, "key-first", alicePrincipal, "127.0.0.1");

        assertNotNull(response);
        assertFalse(response.isReplayed());
        assertEquals(new BigDecimal("500.00"), response.getAmount());
        assertEquals(new BigDecimal("4500.00"), response.getSourceBalanceAfter());
        assertEquals(new BigDecimal("2500.00"), response.getDestinationBalanceAfter());

        // Verify idempotency record persisted with response payload
        verify(idempotencyRecordRepository, times(1)).save(any(IdempotencyRecord.class));
        verify(transactionRepository, times(1)).save(any(LedgerTransaction.class));
    }

    @Test
    @DisplayName("Case B: Exact duplicate request with same key returns original stored result with replayed=true")
    void testExactDuplicateRequestReturnsStoredResult() throws Exception {
        TransferResponse originalResponse = new TransferResponse(
                "TX-ORIG-123",
                TransactionType.TRANSFER,
                10L,
                20L,
                new BigDecimal("500.00"),
                "INR",
                new BigDecimal("4500.00"),
                new BigDecimal("2500.00"),
                TransactionStatus.POSTED,
                "First execution",
                "key-first",
                false,
                java.time.Instant.now(),
                List.of()
        );

        String payloadJson = objectMapper.writeValueAsString(originalResponse);

        // Pre-compute expected hash for: 10|20|500.00|INR|First execution
        // We simulate that the record already exists in the repository
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("500.00"), "First execution");
        String canonical = "10|20|500.00|INR|First execution";
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        String expectedHash = sb.toString();

        IdempotencyRecord storedRecord = new IdempotencyRecord(
                "key-first",
                1L,
                "TRANSFER",
                "/api/v1/transfers",
                expectedHash,
                "TX-ORIG-123",
                "COMPLETED",
                201,
                payloadJson
        );

        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-first"))
                .thenReturn(Optional.of(storedRecord));

        TransferResponse replayedResponse = transferService.transfer(request, "key-first", alicePrincipal, "127.0.0.1");

        assertNotNull(replayedResponse);
        assertTrue(replayedResponse.isReplayed());
        assertEquals("TX-ORIG-123", replayedResponse.getTransactionReference());
        assertEquals(new BigDecimal("500.00"), replayedResponse.getAmount());
        assertEquals(new BigDecimal("4500.00"), replayedResponse.getSourceBalanceAfter());

        // Zero additional database mutations
        verify(accountRepository, never()).findByIdWithLock(any());
        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Case C: Same key with different amount returns HTTP 409 Conflict")
    void testSameKeyDifferentAmountThrowsConflict() throws Exception {
        String canonical = "10|20|500.00|INR|First execution";
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        String originalHash = sb.toString();

        IdempotencyRecord storedRecord = new IdempotencyRecord(
                "key-first",
                1L,
                "TRANSFER",
                "/api/v1/transfers",
                originalHash,
                "TX-ORIG-123",
                "COMPLETED",
                201,
                "{}"
        );

        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-first"))
                .thenReturn(Optional.of(storedRecord));

        // Tampered amount: 600.00 instead of 500.00
        TransferRequest alteredRequest = new TransferRequest(10L, 20L, new BigDecimal("600.00"), "First execution");

        IdempotencyConflictException ex = assertThrows(IdempotencyConflictException.class, () ->
                transferService.transfer(alteredRequest, "key-first", alicePrincipal, "127.0.0.1"));

        assertEquals("key-first", ex.getIdempotencyKey());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Case C: Same key with different source account returns HTTP 409 Conflict")
    void testSameKeyDifferentSourceThrowsConflict() throws Exception {
        String canonical = "10|20|500.00|INR|First execution";
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        String originalHash = sb.toString();

        IdempotencyRecord storedRecord = new IdempotencyRecord(
                "key-first",
                1L,
                "TRANSFER",
                "/api/v1/transfers",
                originalHash,
                "TX-ORIG-123",
                "COMPLETED",
                201,
                "{}"
        );

        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-first"))
                .thenReturn(Optional.of(storedRecord));

        // Tampered source: 11L instead of 10L
        TransferRequest alteredRequest = new TransferRequest(11L, 20L, new BigDecimal("500.00"), "First execution");

        assertThrows(IdempotencyConflictException.class, () ->
                transferService.transfer(alteredRequest, "key-first", alicePrincipal, "127.0.0.1"));
    }

    @Test
    @DisplayName("Case C: Same key with different destination account returns HTTP 409 Conflict")
    void testSameKeyDifferentDestinationThrowsConflict() throws Exception {
        String canonical = "10|20|500.00|INR|First execution";
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        String originalHash = sb.toString();

        IdempotencyRecord storedRecord = new IdempotencyRecord(
                "key-first",
                1L,
                "TRANSFER",
                "/api/v1/transfers",
                originalHash,
                "TX-ORIG-123",
                "COMPLETED",
                201,
                "{}"
        );

        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-first"))
                .thenReturn(Optional.of(storedRecord));

        // Tampered destination: 21L instead of 20L
        TransferRequest alteredRequest = new TransferRequest(10L, 21L, new BigDecimal("500.00"), "First execution");

        assertThrows(IdempotencyConflictException.class, () ->
                transferService.transfer(alteredRequest, "key-first", alicePrincipal, "127.0.0.1"));
    }

    @Test
    @DisplayName("Case E: Different keys execute as separate operations even with identical payload")
    void testDifferentKeysExecuteSeparately() {
        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-A"))
                .thenReturn(Optional.empty());
        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-B"))
                .thenReturn(Optional.empty());

        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(destinationAccount));
        when(transactionRepository.save(any(LedgerTransaction.class))).thenAnswer(i -> i.getArgument(0));

        TransferRequest reqA = new TransferRequest(10L, 20L, new BigDecimal("100.00"), "Payment A");
        TransferRequest reqB = new TransferRequest(10L, 20L, new BigDecimal("100.00"), "Payment B");

        TransferResponse respA = transferService.transfer(reqA, "key-A", alicePrincipal, "127.0.0.1");
        TransferResponse respB = transferService.transfer(reqB, "key-B", alicePrincipal, "127.0.0.1");

        assertNotNull(respA);
        assertNotNull(respB);
        assertFalse(respA.isReplayed());
        assertFalse(respB.isReplayed());

        // Verify two separate ledger transactions and two idempotency records
        verify(transactionRepository, times(2)).save(any(LedgerTransaction.class));
        verify(idempotencyRecordRepository, times(2)).save(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("Failed transfer (insufficient funds) does not leave an unusable idempotency record")
    void testFailedTransferDoesNotPersistIdempotencyRecord() {
        when(idempotencyRecordRepository.findByUserIdAndOperationTypeAndIdempotencyKey(1L, "TRANSFER", "key-fail"))
                .thenReturn(Optional.empty());

        sourceAccount.setBalance(new BigDecimal("100.00"));
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(destinationAccount));

        TransferRequest req = new TransferRequest(10L, 20L, new BigDecimal("500.00"), "Overdraft");

        assertThrows(InsufficientFundsException.class, () ->
                transferService.transfer(req, "key-fail", alicePrincipal, "127.0.0.1"));

        // No idempotency record was saved because the transaction aborted before or at balance check
        verify(idempotencyRecordRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }
}

