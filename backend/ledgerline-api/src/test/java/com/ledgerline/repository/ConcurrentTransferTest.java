package com.ledgerline.repository;

import com.ledgerline.dto.AccountReconciliationResponse;
import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.TransferResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountType;
import com.ledgerline.entity.AuditEvent;
import com.ledgerline.entity.IdempotencyRecord;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.Role;
import com.ledgerline.entity.User;
import com.ledgerline.entity.UserStatus;
import com.ledgerline.exception.IdempotencyConflictException;
import com.ledgerline.exception.InsufficientFundsException;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.ReconciliationService;
import com.ledgerline.service.TransactionService;
import com.ledgerline.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
@DisplayName("Concurrent Transfer & Deadlock Prevention Stress Tests — Real MySQL 8.0 & Testcontainers")
class ConcurrentTransferTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("Test 1 — Concurrent overspending: Two concurrent ₹400 transfers against ₹500 balance; exactly 1 succeeds, 1 fails with 422, final balance strictly ₹100")
    void testConcurrentOverspending() throws InterruptedException {
        Role role = roleRepository.findByName("ROLE_ACCOUNT_HOLDER").orElseThrow();
        long ts = System.currentTimeMillis();

        User userA = userRepository.save(new User("race_xfer_a_" + ts, "race_a_" + ts + "@example.com", "$2a$12$...", "Race A"));
        userA.setRoles(Set.of(role));
        userA = userRepository.save(userA);

        User userB = userRepository.save(new User("race_xfer_b_" + ts, "race_b_" + ts + "@example.com", "$2a$12$...", "Race B"));
        userB.setRoles(Set.of(role));
        userB = userRepository.save(userB);

        UserPrincipal principalA = new UserPrincipal(userA.getId(), userA.getUsername(), userA.getEmail(), userA.getFullName(), userA.getPasswordHash(), UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")));
        UserPrincipal principalB = new UserPrincipal(userB.getId(), userB.getUsername(), userB.getEmail(), userB.getFullName(), userB.getPasswordHash(), UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")));

        Account accountA = accountRepository.save(new Account("LL-RACE-A-" + (ts % 100000), userA.getId(), AccountType.CHECKING, "INR", BigDecimal.ZERO));
        Account accountB = accountRepository.save(new Account("LL-RACE-B-" + (ts % 100000), userB.getId(), AccountType.CHECKING, "INR", BigDecimal.ZERO));

        // Seed ₹500.00 into Account A
        transactionService.deposit(accountA.getId(), new DepositRequest(new BigDecimal("500.00"), "Seed A"), principalA, "127.0.0.1");

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    transferService.transfer(
                            new TransferRequest(accountA.getId(), accountB.getId(), new BigDecimal("400.00"), "Concurrent transfer " + idx),
                            "race-key-" + idx + "-" + ts,
                            principalA,
                            "127.0.0.1"
                    );
                    successCount.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        assertTrue(readyLatch.await(10, TimeUnit.SECONDS));
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, successCount.get(), "Exactly one concurrent transfer must succeed");
        assertEquals(1, failureCount.get(), "Exactly one concurrent transfer must fail with InsufficientFundsException");

        Account finalA = accountRepository.findById(accountA.getId()).orElseThrow();
        Account finalB = accountRepository.findById(accountB.getId()).orElseThrow();

        assertEquals(new BigDecimal("100.00"), finalA.getBalance(), "Account A final balance must be strictly ₹100.00");
        assertEquals(new BigDecimal("400.00"), finalB.getBalance(), "Account B final balance must be strictly ₹400.00");

        AccountReconciliationResponse reconA = reconciliationService.reconcileAccount(accountA.getId(), principalA);
        assertTrue(reconA.isReconciled());
        assertEquals(new BigDecimal("0.00"), reconA.getDiscrepancy());
    }

    @Test
    @DisplayName("Test 2 — Opposite-direction transfers: Account A -> B and Account B -> A concurrently; deterministic ID locking order prevents deadlock")
    void testOppositeDirectionTransfers_deterministicLockingPreventsDeadlock() throws InterruptedException {
        Role role = roleRepository.findByName("ROLE_ACCOUNT_HOLDER").orElseThrow();
        long ts = System.currentTimeMillis();

        User userA = userRepository.save(new User("opp_a_" + ts, "opp_a_" + ts + "@example.com", "$2a$12$...", "Opp A"));
        userA.setRoles(Set.of(role));
        userA = userRepository.save(userA);

        User userB = userRepository.save(new User("opp_b_" + ts, "opp_b_" + ts + "@example.com", "$2a$12$...", "Opp B"));
        userB.setRoles(Set.of(role));
        userB = userRepository.save(userB);

        UserPrincipal principalA = new UserPrincipal(userA.getId(), userA.getUsername(), userA.getEmail(), userA.getFullName(), userA.getPasswordHash(), UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")));
        UserPrincipal principalB = new UserPrincipal(userB.getId(), userB.getUsername(), userB.getEmail(), userB.getFullName(), userB.getPasswordHash(), UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")));

        Account accountA = accountRepository.save(new Account("LL-OPP-A-" + (ts % 100000), userA.getId(), AccountType.CHECKING, "INR", BigDecimal.ZERO));
        Account accountB = accountRepository.save(new Account("LL-OPP-B-" + (ts % 100000), userB.getId(), AccountType.CHECKING, "INR", BigDecimal.ZERO));

        // Seed ₹1,000.00 in each account
        transactionService.deposit(accountA.getId(), new DepositRequest(new BigDecimal("1000.00"), "Seed A"), principalA, "127.0.0.1");
        transactionService.deposit(accountB.getId(), new DepositRequest(new BigDecimal("1000.00"), "Seed B"), principalB, "127.0.0.1");

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger completedCount = new AtomicInteger(0);

        // Thread 1: A -> B ₹300.00
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                transferService.transfer(
                        new TransferRequest(accountA.getId(), accountB.getId(), new BigDecimal("300.00"), "A to B"),
                        "opp-key-1-" + ts,
                        principalA,
                        "127.0.0.1"
                );
                completedCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Thread 2: B -> A ₹200.00
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                transferService.transfer(
                        new TransferRequest(accountB.getId(), accountA.getId(), new BigDecimal("200.00"), "B to A"),
                        "opp-key-2-" + ts,
                        principalB,
                        "127.0.0.1"
                );
                completedCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        assertTrue(readyLatch.await(10, TimeUnit.SECONDS));
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Transfers must complete without deadlocking");

        assertEquals(2, completedCount.get(), "Both opposite-direction transfers must complete successfully");

        Account finalA = accountRepository.findById(accountA.getId()).orElseThrow();
        Account finalB = accountRepository.findById(accountB.getId()).orElseThrow();

        // Initial 1000 - 300 + 200 = 900
        assertEquals(new BigDecimal("900.00"), finalA.getBalance());
        // Initial 1000 + 300 - 200 = 1100
        assertEquals(new BigDecimal("1100.00"), finalB.getBalance());

        // Total money conserved
        assertEquals(new BigDecimal("2000.00"), finalA.getBalance().add(finalB.getBalance()));
    }

    @Test
    @DisplayName("Test 3 — Concurrent duplicate key: Three identical requests with same key executed concurrently; exactly 1 transfer occurs, all resolve to same result")
    void testConcurrentDuplicateKey() throws InterruptedException {
        Role role = roleRepository.findByName("ROLE_ACCOUNT_HOLDER").orElseThrow();
        long ts = System.currentTimeMillis();

        User userA = userRepository.save(new User("dup_a_" + ts, "dup_a_" + ts + "@example.com", "$2a$12$...", "Dup A"));
        userA.setRoles(Set.of(role));
        userA = userRepository.save(userA);

        User userB = userRepository.save(new User("dup_b_" + ts, "dup_b_" + ts + "@example.com", "$2a$12$...", "Dup B"));
        userB.setRoles(Set.of(role));
        userB = userRepository.save(userB);

        UserPrincipal principalA = new UserPrincipal(userA.getId(), userA.getUsername(), userA.getEmail(), userA.getFullName(), userA.getPasswordHash(), UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")));
        UserPrincipal principalB = new UserPrincipal(userB.getId(), userB.getUsername(), userB.getEmail(), userB.getFullName(), userB.getPasswordHash(), UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")));

        Account accountA = accountRepository.save(new Account("LL-DUP-A-" + (ts % 100000), userA.getId(), AccountType.CHECKING, "INR", BigDecimal.ZERO));
        Account accountB = accountRepository.save(new Account("LL-DUP-B-" + (ts % 100000), userB.getId(), AccountType.CHECKING, "INR", BigDecimal.ZERO));

        transactionService.deposit(accountA.getId(), new DepositRequest(new BigDecimal("2000.00"), "Seed A"), principalA, "127.0.0.1");

        int threadCount = 3;
        String sharedKey = "shared-dup-key-" + ts;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<TransferResponse> responses = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    TransferResponse resp = transferService.transfer(
                            new TransferRequest(accountA.getId(), accountB.getId(), new BigDecimal("500.00"), "Duplicate key test"),
                            sharedKey,
                            principalA,
                            "127.0.0.1"
                    );
                    responses.add(resp);
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                }
            });
        }

        assertTrue(readyLatch.await(10, TimeUnit.SECONDS));
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(0, errorCount.get(), "Zero errors expected for concurrent duplicate requests");
        assertEquals(3, responses.size(), "All 3 threads must receive a response");

        // All responses must share the exact same transactionReference
        String expectedTxRef = responses.get(0).getTransactionReference();
        for (TransferResponse resp : responses) {
            assertEquals(expectedTxRef, resp.getTransactionReference(), "All responses must refer to the same transaction reference");
        }

        // Account balances: Deducted exactly once!
        Account finalA = accountRepository.findById(accountA.getId()).orElseThrow();
        Account finalB = accountRepository.findById(accountB.getId()).orElseThrow();
        assertEquals(new BigDecimal("1500.00"), finalA.getBalance(), "Account A balance must reflect exactly 1 deduction of ₹500");
        assertEquals(new BigDecimal("500.00"), finalB.getBalance(), "Account B balance must reflect exactly 1 credit of ₹500");

        // Exactly 1 idempotency record in MySQL
        Optional<IdempotencyRecord> idempOpt = idempotencyRecordRepository
                .findByUserIdAndOperationTypeAndIdempotencyKey(principalA.getId(), "TRANSFER", sharedKey);
        assertTrue(idempOpt.isPresent());
    }

    @Test
    @DisplayName("Test 4 — Same key, conflicting payload: Two concurrent requests with same key but different amounts; exactly 1 accepted, 1 rejected with conflict (409)")
    void testSameKeyConflictingPayload() throws InterruptedException {
        Role role = roleRepository.findByName("ROLE_ACCOUNT_HOLDER").orElseThrow();
        long ts = System.currentTimeMillis();

        User userA = userRepository.save(new User("conf_a_" + ts, "conf_a_" + ts + "@example.com", "$2a$12$...", "Conf A"));
        userA.setRoles(Set.of(role));
        userA = userRepository.save(userA);

        User userB = userRepository.save(new User("conf_b_" + ts, "conf_b_" + ts + "@example.com", "$2a$12$...", "Conf B"));
        userB.setRoles(Set.of(role));
        userB = userRepository.save(userB);

        UserPrincipal principalA = new UserPrincipal(userA.getId(), userA.getUsername(), userA.getEmail(), userA.getFullName(), userA.getPasswordHash(), UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")));
        UserPrincipal principalB = new UserPrincipal(userB.getId(), userB.getUsername(), userB.getEmail(), userB.getFullName(), userB.getPasswordHash(), UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")));

        Account accountA = accountRepository.save(new Account("LL-CONF-A-" + (ts % 100000), userA.getId(), AccountType.CHECKING, "INR", BigDecimal.ZERO));
        Account accountB = accountRepository.save(new Account("LL-CONF-B-" + (ts % 100000), userB.getId(), AccountType.CHECKING, "INR", BigDecimal.ZERO));

        transactionService.deposit(accountA.getId(), new DepositRequest(new BigDecimal("2000.00"), "Seed A"), principalA, "127.0.0.1");

        int threadCount = 2;
        String sharedKey = "conflict-key-" + ts;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // Thread 1: Amount ₹100.00
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                transferService.transfer(
                        new TransferRequest(accountA.getId(), accountB.getId(), new BigDecimal("100.00"), "Transfer 100"),
                        sharedKey,
                        principalA,
                        "127.0.0.1"
                );
                successCount.incrementAndGet();
            } catch (IdempotencyConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Thread 2: Amount ₹200.00
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                transferService.transfer(
                        new TransferRequest(accountA.getId(), accountB.getId(), new BigDecimal("200.00"), "Transfer 200"),
                        sharedKey,
                        principalA,
                        "127.0.0.1"
                );
                successCount.incrementAndGet();
            } catch (IdempotencyConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        assertTrue(readyLatch.await(10, TimeUnit.SECONDS));
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, successCount.get(), "Exactly one payload must be accepted");
        assertEquals(1, conflictCount.get(), "The competing payload must be rejected with IdempotencyConflictException (409)");

        // Only 1 transfer executed
        Account finalA = accountRepository.findById(accountA.getId()).orElseThrow();
        assertTrue(
                finalA.getBalance().compareTo(new BigDecimal("1900.00")) == 0 ||
                        finalA.getBalance().compareTo(new BigDecimal("1800.00")) == 0,
                "Final balance must reflect exactly 1 accepted transfer (₹1900 or ₹1800)"
        );
    }
}
