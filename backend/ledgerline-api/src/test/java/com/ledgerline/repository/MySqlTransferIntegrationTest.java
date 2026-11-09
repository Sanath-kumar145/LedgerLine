package com.ledgerline.repository;

import com.ledgerline.dto.AccountReconciliationResponse;
import com.ledgerline.dto.AccountStatementResponse;
import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.SystemReconciliationResponse;
import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.TransferResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountType;
import com.ledgerline.entity.AuditEvent;
import com.ledgerline.entity.EntryType;
import com.ledgerline.entity.IdempotencyRecord;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.LedgerTransaction;
import com.ledgerline.entity.Role;
import com.ledgerline.entity.TransactionStatus;
import com.ledgerline.entity.TransactionType;
import com.ledgerline.entity.User;
import com.ledgerline.entity.UserStatus;
import com.ledgerline.exception.IdempotencyConflictException;
import com.ledgerline.exception.InsufficientFundsException;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.AuditService;
import com.ledgerline.service.ReconciliationService;
import com.ledgerline.service.TransactionService;
import com.ledgerline.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("MySQL Transfer & Idempotency Integration Tests — Real MySQL 8.0 & Flyway V3")
class MySqlTransferIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerTransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UserPrincipal alicePrincipal;
    private UserPrincipal bobPrincipal;
    private Account aliceAccount;
    private Account bobAccount;

    @BeforeEach
    void setUp() {
        Role holderRole = roleRepository.findByName("ROLE_ACCOUNT_HOLDER")
                .orElseThrow(() -> new IllegalStateException("ROLE_ACCOUNT_HOLDER missing"));

        long ts = System.currentTimeMillis();
        String aliceUname = "xfer_alice_" + ts;
        User alice = new User(aliceUname, aliceUname + "@example.com", "$2a$12$eX4mP1eH4sH...", "Alice Test");
        alice.setRoles(Set.of(holderRole));
        alice = userRepository.save(alice);

        String bobUname = "xfer_bob_" + ts;
        User bob = new User(bobUname, bobUname + "@example.com", "$2a$12$eX4mP1eH4sH...", "Bob Test");
        bob.setRoles(Set.of(holderRole));
        bob = userRepository.save(bob);

        alicePrincipal = new UserPrincipal(
                alice.getId(), alice.getUsername(), alice.getEmail(), alice.getFullName(),
                alice.getPasswordHash(), UserStatus.ACTIVE,
                List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        bobPrincipal = new UserPrincipal(
                bob.getId(), bob.getUsername(), bob.getEmail(), bob.getFullName(),
                bob.getPasswordHash(), UserStatus.ACTIVE,
                List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        aliceAccount = accountRepository.save(new Account("LL-IN-ALICE-" + (ts % 100000), alice.getId(), AccountType.CHECKING, "INR", BigDecimal.ZERO));
        bobAccount = accountRepository.save(new Account("LL-IN-BOB-" + (ts % 100000), bob.getId(), AccountType.SAVINGS, "INR", BigDecimal.ZERO));

        // Seed initial ₹5,000.00 for Alice and ₹2,000.00 for Bob through genuine double-entry deposits
        transactionService.deposit(aliceAccount.getId(), new DepositRequest(new BigDecimal("5000.00"), "Alice initial deposit"), alicePrincipal, "127.0.0.1");
        transactionService.deposit(bobAccount.getId(), new DepositRequest(new BigDecimal("2000.00"), "Bob initial deposit"), bobPrincipal, "127.0.0.1");

        // Reload accounts to get updated balances
        aliceAccount = accountRepository.findById(aliceAccount.getId()).orElseThrow();
        bobAccount = accountRepository.findById(bobAccount.getId()).orElseThrow();
    }

    @Test
    @DisplayName("End-to-End Transfer against MySQL 8.0: Alice (₹5000) -> Bob (₹2000) of ₹1000 updates balances, posts double entries, saves idempotency record, and reconciles perfectly")
    void testEndToEndTransfer_mysqlVerification() {
        String key = "integ-key-" + System.currentTimeMillis();
        TransferRequest request = new TransferRequest(aliceAccount.getId(), bobAccount.getId(), new BigDecimal("1000.00"), "Integration transfer");

        // 1. Execute transfer
        TransferResponse response = transferService.transfer(request, key, alicePrincipal, "127.0.0.1");

        assertNotNull(response);
        assertEquals(new BigDecimal("1000.00"), response.getAmount());
        assertEquals("INR", response.getCurrency());
        assertEquals(new BigDecimal("4000.00"), response.getSourceBalanceAfter());
        assertEquals(new BigDecimal("3000.00"), response.getDestinationBalanceAfter());
        assertFalse(response.isReplayed());
        assertEquals(TransactionStatus.POSTED, response.getStatus());

        // 2. Verify account balances in MySQL
        Account refreshedAlice = accountRepository.findById(aliceAccount.getId()).orElseThrow();
        Account refreshedBob = accountRepository.findById(bobAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("4000.00"), refreshedAlice.getBalance());
        assertEquals(new BigDecimal("3000.00"), refreshedBob.getBalance());

        // 3. Verify ledger transaction in MySQL
        LedgerTransaction tx = transactionRepository.findByTransactionReference(response.getTransactionReference()).orElseThrow();
        assertEquals(TransactionType.TRANSFER, tx.getTransactionType());
        assertEquals(new BigDecimal("1000.00"), tx.getTotalAmount());
        assertEquals(alicePrincipal.getId(), tx.getInitiatedByUserId());
        assertEquals(key, tx.getIdempotencyKey());
        assertEquals(TransactionStatus.POSTED, tx.getStatus());

        // 4. Verify balanced double-entry postings in MySQL
        List<LedgerEntry> entries = entryRepository.findByTransactionId(tx.getId());
        assertEquals(2, entries.size());

        BigDecimal totalDebits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("1000.00"), totalDebits);
        assertEquals(new BigDecimal("1000.00"), totalCredits);
        assertEquals(totalDebits, totalCredits, "Double-entry conservation must hold: total debits == total credits");

        // 5. Verify Idempotency Record in MySQL
        Optional<IdempotencyRecord> idempOpt = idempotencyRecordRepository
                .findByUserIdAndOperationTypeAndIdempotencyKey(alicePrincipal.getId(), "TRANSFER", key);
        assertTrue(idempOpt.isPresent());
        IdempotencyRecord idemp = idempOpt.get();
        assertEquals("COMPLETED", idemp.getStatus());
        assertEquals(201, idemp.getResponseCode());
        assertEquals(tx.getTransactionReference(), idemp.getTransactionReference());
        assertNotNull(idemp.getRequestHash());
        assertNotNull(idemp.getResponsePayload());

        // 6. Test Idempotent Replay: exact duplicate request returns original result without extra ledger entries
        int entryCountBefore = entryRepository.findByAccountIdOrderByPostedAtDesc(aliceAccount.getId()).size();
        TransferResponse replayed = transferService.transfer(request, key, alicePrincipal, "127.0.0.1");
        assertTrue(replayed.isReplayed());
        assertEquals(response.getTransactionReference(), replayed.getTransactionReference());
        assertEquals(new BigDecimal("4000.00"), replayed.getSourceBalanceAfter());
        int entryCountAfter = entryRepository.findByAccountIdOrderByPostedAtDesc(aliceAccount.getId()).size();
        assertEquals(entryCountBefore, entryCountAfter, "Replayed request must not create duplicate ledger entries");

        // 7. Test Idempotency Conflict: same key with different amount rejected with 409
        TransferRequest alteredRequest = new TransferRequest(aliceAccount.getId(), bobAccount.getId(), new BigDecimal("1500.00"), "Altered");
        assertThrows(IdempotencyConflictException.class, () ->
                transferService.transfer(alteredRequest, key, alicePrincipal, "127.0.0.1"));

        // 8. Verify Account Statements
        AccountStatementResponse aliceStmt = transactionService.getAccountStatement(aliceAccount.getId(), alicePrincipal);
        assertEquals(2, aliceStmt.getTotalEntries()); // 1 deposit + 1 transfer debit
        assertTrue(aliceStmt.getEntries().stream().anyMatch(e -> e.getEntryType() == EntryType.DEBIT && e.getAmount().compareTo(new BigDecimal("1000.00")) == 0));

        AccountStatementResponse bobStmt = transactionService.getAccountStatement(bobAccount.getId(), bobPrincipal);
        assertEquals(2, bobStmt.getTotalEntries()); // 1 deposit + 1 transfer credit
        assertTrue(bobStmt.getEntries().stream().anyMatch(e -> e.getEntryType() == EntryType.CREDIT && e.getAmount().compareTo(new BigDecimal("1000.00")) == 0));

        // 9. Verify Live Mathematical Reconciliation
        AccountReconciliationResponse aliceRecon = reconciliationService.reconcileAccount(aliceAccount.getId(), alicePrincipal);
        assertTrue(aliceRecon.isReconciled());
        assertEquals(new BigDecimal("0.00"), aliceRecon.getDiscrepancy());
        assertEquals(new BigDecimal("4000.00"), aliceRecon.getMaterializedBalance());

        AccountReconciliationResponse bobRecon = reconciliationService.reconcileAccount(bobAccount.getId(), bobPrincipal);
        assertTrue(bobRecon.isReconciled());
        assertEquals(new BigDecimal("0.00"), bobRecon.getDiscrepancy());
        assertEquals(new BigDecimal("3000.00"), bobRecon.getMaterializedBalance());

        UserPrincipal auditorPrincipal = new UserPrincipal(
                999L, "auditor_user", "auditor@example.com", "Auditor General", "hash", UserStatus.ACTIVE,
                List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"))
        );

        SystemReconciliationResponse sysRecon = reconciliationService.reconcileSystem(auditorPrincipal);
        assertEquals("SYSTEM_BALANCED", sysRecon.getIntegrityStatus());
        assertEquals(new BigDecimal("0.00"), sysRecon.getSystemDelta());
        assertEquals(sysRecon.getSystemTotalDebits(), sysRecon.getSystemTotalCredits());

        // 10. Verify Consistent Audit Event in MySQL
        List<AuditEvent> completedEvents = auditEventRepository.findByEventTypeOrderByCreatedAtDesc("TRANSFER_COMPLETED");
        Optional<AuditEvent> matchedCompleted = completedEvents.stream()
                .filter(e -> e.getUserId().equals(alicePrincipal.getId())
                        && e.getAccountId().equals(aliceAccount.getId())
                        && e.getDetails() != null
                        && e.getDetails().contains(tx.getTransactionReference()))
                .findFirst();
        assertTrue(matchedCompleted.isPresent(), "TRANSFER_COMPLETED audit event must be persisted in MySQL upon successful transfer commit");
    }

    @Test
    @DisplayName("Rollback Safety: Forced transaction rollback after recordConsistentEvent leaves NO balance changes, NO ledger entries, and NO TRANSFER_COMPLETED audit event in MySQL")
    void testTransferRollback_clearsConsistentAuditEventAndBalancesAndEntries() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        BigDecimal aliceInitial = aliceAccount.getBalance();
        BigDecimal bobInitial = bobAccount.getBalance();
        String rollbackKey = "rollback-test-key-" + System.currentTimeMillis();

        // Execute transaction that mutates accounts, posts consistent audit event, and then fails
        assertThrows(RuntimeException.class, () -> {
            template.execute(status -> {
                // Mutate balances
                aliceAccount.setBalance(aliceInitial.subtract(new BigDecimal("500.00")));
                bobAccount.setBalance(bobInitial.add(new BigDecimal("500.00")));
                accountRepository.save(aliceAccount);
                accountRepository.save(bobAccount);

                // Write consistent audit event (Propagation.REQUIRED - must rollback)
                auditService.recordConsistentEvent(
                        "TRANSFER_COMPLETED",
                        alicePrincipal.getId(),
                        aliceAccount.getId(),
                        "Rollback test audit entry for " + rollbackKey,
                        "127.0.0.1"
                );

                // Write autonomous audit event in the same method to prove autonomous survives
                auditService.recordAutonomousEvent(
                        "AUTONOMOUS_TEST_EVENT",
                        alicePrincipal.getId(),
                        aliceAccount.getId(),
                        "Autonomous audit entry for " + rollbackKey,
                        "127.0.0.1"
                );

                // Simulate catastrophic unexpected failure before commit
                throw new RuntimeException("Forced rollback failure simulation");
            });
        });

        // 1. Verify balances in MySQL are 100% untouched
        Account refreshedAlice = accountRepository.findById(aliceAccount.getId()).orElseThrow();
        Account refreshedBob = accountRepository.findById(bobAccount.getId()).orElseThrow();
        assertEquals(aliceInitial, refreshedAlice.getBalance(), "Alice balance must not be modified after rollback");
        assertEquals(bobInitial, refreshedBob.getBalance(), "Bob balance must not be modified after rollback");

        // 2. Verify that consistent audit event TRANSFER_COMPLETED was ROLLED BACK and DOES NOT exist in MySQL
        List<AuditEvent> completedEvents = auditEventRepository.findByEventTypeOrderByCreatedAtDesc("TRANSFER_COMPLETED");
        boolean rolledBackEventExists = completedEvents.stream()
                .anyMatch(e -> e.getDetails() != null && e.getDetails().contains(rollbackKey));
        assertFalse(rolledBackEventExists, "Consistent TRANSFER_COMPLETED audit event must be rolled back atomically with the transaction");

        // 3. Verify that autonomous audit event DID persist in MySQL despite outer rollback
        List<AuditEvent> autonomousEvents = auditEventRepository.findByEventTypeOrderByCreatedAtDesc("AUTONOMOUS_TEST_EVENT");
        boolean autonomousEventExists = autonomousEvents.stream()
                .anyMatch(e -> e.getDetails() != null && e.getDetails().contains(rollbackKey));
        assertTrue(autonomousEventExists, "Autonomous audit event must persist in MySQL even when outer transaction rolls back");
    }

    @Test
    @DisplayName("Refusal Audit Persistence: Insufficient funds refusal rolls back financial mutation but permanently preserves TRANSFER_REFUSED_INSUFFICIENT_FUNDS audit event in MySQL")
    void testTransferRefused_insufficientFunds_preservesRefusalAuditEventInMySql() {
        BigDecimal aliceInitial = aliceAccount.getBalance();
        BigDecimal bobInitial = bobAccount.getBalance();
        String refusalKey = "refusal-key-" + System.currentTimeMillis();

        TransferRequest overdraftRequest = new TransferRequest(
                aliceAccount.getId(), bobAccount.getId(), new BigDecimal("99999.00"), "Overdraft attempt"
        );

        assertThrows(InsufficientFundsException.class, () ->
                transferService.transfer(overdraftRequest, refusalKey, alicePrincipal, "127.0.0.1")
        );

        // 1. Verify balances in MySQL are completely unchanged
        Account refreshedAlice = accountRepository.findById(aliceAccount.getId()).orElseThrow();
        Account refreshedBob = accountRepository.findById(bobAccount.getId()).orElseThrow();
        assertEquals(aliceInitial, refreshedAlice.getBalance());
        assertEquals(bobInitial, refreshedBob.getBalance());

        // 2. Verify NO idempotency record was created
        Optional<IdempotencyRecord> idempOpt = idempotencyRecordRepository
                .findByUserIdAndOperationTypeAndIdempotencyKey(alicePrincipal.getId(), "TRANSFER", refusalKey);
        assertFalse(idempOpt.isPresent(), "No idempotency record should be persisted for refused transfer");

        // 3. Verify NO TRANSFER_COMPLETED event exists for this refusal key
        List<AuditEvent> completedEvents = auditEventRepository.findByEventTypeOrderByCreatedAtDesc("TRANSFER_COMPLETED");
        boolean falseCompletedExists = completedEvents.stream()
                .anyMatch(e -> e.getDetails() != null && e.getDetails().contains(refusalKey));
        assertFalse(falseCompletedExists, "No TRANSFER_COMPLETED event must exist for refused transfer");

        // 4. Verify autonomous TRANSFER_REFUSED_INSUFFICIENT_FUNDS audit event IS persisted in MySQL
        List<AuditEvent> refusalEvents = auditEventRepository.findByEventTypeOrderByCreatedAtDesc("TRANSFER_REFUSED_INSUFFICIENT_FUNDS");
        Optional<AuditEvent> matchedRefusal = refusalEvents.stream()
                .filter(e -> e.getUserId().equals(alicePrincipal.getId())
                        && e.getAccountId().equals(aliceAccount.getId())
                        && e.getDetails() != null
                        && e.getDetails().contains("refused: insufficient funds"))
                .findFirst();

        assertTrue(matchedRefusal.isPresent(), "Autonomous refusal audit event MUST be persisted in MySQL");
        assertTrue(matchedRefusal.get().getDetails().contains("available: " + aliceInitial));
    }
}
