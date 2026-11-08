package com.ledgerline.repository;

import com.ledgerline.dto.AccountReconciliationResponse;
import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.SystemReconciliationResponse;
import com.ledgerline.dto.TransactionResponse;
import com.ledgerline.dto.WithdrawalRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountType;
import com.ledgerline.entity.AuditEvent;
import com.ledgerline.entity.EntryType;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.Role;
import com.ledgerline.entity.TransactionStatus;
import com.ledgerline.entity.TransactionType;
import com.ledgerline.entity.User;
import com.ledgerline.entity.UserStatus;
import com.ledgerline.exception.InsufficientFundsException;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.ReconciliationService;
import com.ledgerline.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("MySQL Ledger Integration Tests — Real MySQL 8.0 & Flyway V2")
class MySqlLedgerIntegrationTest {

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
    private AuditEventRepository auditEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User testUser;
    private UserPrincipal userPrincipal;
    private UserPrincipal auditorPrincipal;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        Role holderRole = roleRepository.findByName("ROLE_ACCOUNT_HOLDER")
                .orElseThrow(() -> new IllegalStateException("ROLE_ACCOUNT_HOLDER missing"));
        Role auditorRole = roleRepository.findByName("ROLE_AUDITOR")
                .orElseThrow(() -> new IllegalStateException("ROLE_AUDITOR missing"));

        String uniqueUsername = "ledger_user_" + System.currentTimeMillis();
        testUser = new User(uniqueUsername, uniqueUsername + "@example.com", "$2a$12$eX4mP1eH4sH...", "Ledger Test User");
        testUser.setRoles(Set.of(holderRole));
        testUser = userRepository.save(testUser);

        userPrincipal = new UserPrincipal(
                testUser.getId(),
                testUser.getUsername(),
                testUser.getEmail(),
                testUser.getFullName(),
                testUser.getPasswordHash(),
                testUser.getStatus(),
                List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        auditorPrincipal = new UserPrincipal(
                999L,
                "auditor_user",
                "auditor@ledgerline.internal",
                "Auditor Officer",
                "hash",
                UserStatus.ACTIVE,
                List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"))
        );

        String uniqueAccNumber = "LL-IN-TEST-" + (System.currentTimeMillis() % 100000);
        testAccount = new Account(uniqueAccNumber, testUser.getId(), AccountType.CHECKING, "INR", BigDecimal.ZERO);
        testAccount = accountRepository.save(testAccount);
    }

    @Test
    @DisplayName("Deposit and withdrawal lifecycle verifies double-entry balance and reconciliation against real MySQL")
    void testDepositAndWithdrawalFlow_withBalancedLedgerEntries() {
        // 1. Deposit ₹1000.00
        DepositRequest depositReq = new DepositRequest(new BigDecimal("1000.00"), "Initial funding");
        TransactionResponse depResponse = transactionService.deposit(testAccount.getId(), depositReq, userPrincipal, "127.0.0.1");

        assertNotNull(depResponse);
        assertEquals(TransactionStatus.POSTED, depResponse.getStatus());
        assertEquals(TransactionType.DEPOSIT, depResponse.getTransactionType());

        // Verify account balance updated in DB
        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(updatedAccount.getBalance()));

        // Verify ledger entries for deposit
        List<LedgerEntry> entries = entryRepository.findByAccountIdOrderByPostedAtDesc(testAccount.getId());
        assertFalse(entries.isEmpty());
        assertEquals(1, entries.size(), "Customer account has 1 credit entry for deposit");
        assertEquals(EntryType.CREDIT, entries.get(0).getEntryType());
        assertEquals(0, new BigDecimal("1000.00").compareTo(entries.get(0).getAmount()));

        // 2. Withdraw ₹350.00
        WithdrawalRequest withReq = new WithdrawalRequest(new BigDecimal("350.00"), "Cash withdrawal");
        TransactionResponse withResponse = transactionService.withdraw(testAccount.getId(), withReq, userPrincipal, "127.0.0.1");

        assertNotNull(withResponse);
        assertEquals(TransactionStatus.POSTED, withResponse.getStatus());
        assertEquals(TransactionType.WITHDRAWAL, withResponse.getTransactionType());

        // Verify account balance is exactly ₹650.00
        updatedAccount = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("650.00").compareTo(updatedAccount.getBalance()));

        // 3. Reconcile account
        AccountReconciliationResponse accRecon = reconciliationService.reconcileAccount(testAccount.getId(), userPrincipal);
        assertTrue(accRecon.isReconciled());
        assertEquals(0, new BigDecimal("650.00").compareTo(accRecon.getMaterializedBalance()));
        assertEquals(0, new BigDecimal("650.00").compareTo(accRecon.getComputedLedgerBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(accRecon.getDiscrepancy()));

        // 4. Reconcile entire system
        SystemReconciliationResponse sysRecon = reconciliationService.reconcileSystem(auditorPrincipal);
        assertEquals("SYSTEM_BALANCED", sysRecon.getIntegrityStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(sysRecon.getSystemDelta()));
        assertEquals(0, sysRecon.getUnbalancedTransactionCount());
    }

    @Test
    @DisplayName("Overdraft attempt is rejected with InsufficientFundsException and records AuditEvent")
    void testOverdraftPrevention_andAuditEvent() {
        // Current balance is 0.00; attempt to withdraw ₹100.00
        WithdrawalRequest withReq = new WithdrawalRequest(new BigDecimal("100.00"), "Overdraft attempt");

        assertThrows(InsufficientFundsException.class, () ->
                transactionService.withdraw(testAccount.getId(), withReq, userPrincipal, "127.0.0.1")
        );

        // Verify balance remains exactly 0.00
        Account unchanged = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(unchanged.getBalance()));

        // Verify audit event persisted
        List<AuditEvent> auditEvents = auditEventRepository.findByAccountIdOrderByCreatedAtDesc(testAccount.getId());
        assertFalse(auditEvents.isEmpty());
        assertEquals("WITHDRAWAL_REFUSED_INSUFFICIENT_FUNDS", auditEvents.get(0).getEventType());
    }
}
