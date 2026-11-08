package com.ledgerline.repository;

import com.ledgerline.dto.AccountReconciliationResponse;
import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.WithdrawalRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountType;
import com.ledgerline.entity.AuditEvent;
import com.ledgerline.entity.EntryType;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.Role;
import com.ledgerline.entity.User;
import com.ledgerline.exception.InsufficientFundsException;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.ReconciliationService;
import com.ledgerline.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Concurrent Withdrawal Stress Tests — Pessimistic Row Locking & Race Condition Prevention")
class ConcurrentWithdrawalTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("Two concurrent ₹400 withdrawals against ₹500 balance: exactly 1 succeeds, 1 fails with 422, final balance is strictly ₹100")
    void testConcurrentWithdrawals_pessimisticLockPreventsDoubleSpend() throws InterruptedException {
        // 1. Seed user and account with ₹500.00
        Role holderRole = roleRepository.findByName("ROLE_ACCOUNT_HOLDER")
                .orElseThrow(() -> new IllegalStateException("ROLE_ACCOUNT_HOLDER missing"));

        String uniqueUsername = "race_user_" + System.currentTimeMillis();
        User user = new User(uniqueUsername, uniqueUsername + "@example.com", "$2a$12$eX4mP1eH4sH...", "Race Test User");
        user.setRoles(Set.of(holderRole));
        user = userRepository.save(user);

        UserPrincipal userPrincipal = new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getPasswordHash(),
                user.getStatus(),
                List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        String accNumber = "LL-RACE-" + (System.currentTimeMillis() % 100000);
        Account account = new Account(accNumber, user.getId(), AccountType.CHECKING, "INR", BigDecimal.ZERO);
        account = accountRepository.save(account);
        Long accountId = account.getId();

        // Deposit initial ₹500.00 through genuine double-entry ledger so all ledger balance invariants hold
        transactionService.deposit(accountId, new DepositRequest(new BigDecimal("500.00"), "Seed funding"), userPrincipal, "127.0.0.1");

        // 2. Prepare concurrent execution
        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadIdx = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    WithdrawalRequest request = new WithdrawalRequest(
                            new BigDecimal("400.00"),
                            "Concurrent withdrawal attempt " + threadIdx
                    );
                    transactionService.withdraw(accountId, request, userPrincipal, "127.0.0.1");
                    successCount.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    failureCount.incrementAndGet();
                } catch (Throwable t) {
                    System.err.println("Thread " + threadIdx + " failed unexpectedly: " + t);
                    t.printStackTrace();
                }
            });
        }

        // 3. Align threads at starting gate and fire simultaneously
        assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "Threads must be ready within 5 seconds");
        startLatch.countDown();

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Executor must terminate within 30 seconds");

        // 4. Assert invariants
        assertEquals(1, successCount.get(), "Exactly one withdrawal request must succeed");
        assertEquals(1, failureCount.get(), "Exactly one withdrawal request must fail due to insufficient funds");

        // Materialized balance must be strictly ₹100.00, NEVER negative (-₹300.00)
        Account finalAccount = accountRepository.findById(accountId).orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(finalAccount.getBalance()),
                "Final balance must be exactly ₹100.00 (not negative or double-spent)");

        // Customer account has 1 deposit credit of ₹500 and 1 withdrawal debit of ₹400
        List<LedgerEntry> customerEntries = entryRepository.findByAccountIdOrderByPostedAtDesc(accountId);
        assertEquals(2, customerEntries.size(), "Customer account must have 2 entries (1 initial deposit, 1 withdrawal)");
        assertEquals(EntryType.DEBIT, customerEntries.get(0).getEntryType());
        assertEquals(0, new BigDecimal("400.00").compareTo(customerEntries.get(0).getAmount()));
        assertEquals(EntryType.CREDIT, customerEntries.get(1).getEntryType());
        assertEquals(0, new BigDecimal("500.00").compareTo(customerEntries.get(1).getAmount()));

        // Exactly one audit event for refused withdrawal must be logged
        List<AuditEvent> auditEvents = auditEventRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        assertFalse(auditEvents.isEmpty(), "Audit event must be logged for the failed withdrawal");
        assertEquals("WITHDRAWAL_REFUSED_INSUFFICIENT_FUNDS", auditEvents.get(0).getEventType());

        // Account reconciliation must confirm the ledger is in complete balance
        AccountReconciliationResponse recon = reconciliationService.reconcileAccount(accountId, userPrincipal);
        assertTrue(recon.isReconciled());
        assertEquals(0, BigDecimal.ZERO.compareTo(recon.getDiscrepancy()));
    }
}
