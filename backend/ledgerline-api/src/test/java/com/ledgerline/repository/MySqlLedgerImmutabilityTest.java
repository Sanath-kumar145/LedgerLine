package com.ledgerline.repository;

import com.ledgerline.dto.DepositRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountType;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.LedgerTransaction;
import com.ledgerline.entity.Role;
import com.ledgerline.entity.User;
import com.ledgerline.entity.UserStatus;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.TransactionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Ledger Immutability Protection Tests — Defense-in-Depth Append-Only Ledger")
class MySqlLedgerImmutabilityTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    @Autowired
    private LedgerTransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UserPrincipal testPrincipal;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        Role holderRole = roleRepository.findByName("ROLE_ACCOUNT_HOLDER")
                .orElseThrow(() -> new IllegalStateException("ROLE_ACCOUNT_HOLDER missing"));

        long ts = System.currentTimeMillis();
        String uname = "immut_user_" + ts;
        User user = new User(uname, uname + "@example.com", "$2a$12$eX4mP1eH4sH...", "Immutability User");
        user.setRoles(Set.of(holderRole));
        user = userRepository.save(user);

        testPrincipal = new UserPrincipal(
                user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                user.getPasswordHash(), UserStatus.ACTIVE,
                List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        testAccount = accountRepository.save(
                new Account("LL-IN-IMMUT-" + (ts % 100000), user.getId(), AccountType.CHECKING, "INR", BigDecimal.ZERO)
        );

        // Execute a legitimate deposit to create ledger entries and transactions
        transactionService.deposit(
                testAccount.getId(),
                new DepositRequest(new BigDecimal("1000.00"), "Deposit for immutability check"),
                testPrincipal,
                "127.0.0.1"
        );
    }

    @Test
    @DisplayName("Attempted ledger entry deletion via Repository is blocked by immutability enforcement")
    void testAttemptedLedgerEntryDeletion_viaRepository_isRejected() {
        List<LedgerEntry> entries = entryRepository.findByAccountIdOrderByPostedAtDesc(testAccount.getId());
        assertNotNull(entries);
        assertTrue(entries.size() > 0, "Ledger entries must exist from deposit");
        LedgerEntry entry = entries.get(0);

        Exception ex = assertThrows(UnsupportedOperationException.class, () ->
                entryRepository.delete(entry)
        );

        assertTrue(ex.getMessage().contains("immutable and cannot be deleted"));
    }

    @Test
    @DisplayName("Attempted ledger entry deletion by ID via Repository is blocked by immutability enforcement")
    void testAttemptedLedgerEntryDeletionById_viaRepository_isRejected() {
        List<LedgerEntry> entries = entryRepository.findByAccountIdOrderByPostedAtDesc(testAccount.getId());
        Long entryId = entries.get(0).getId();

        Exception ex = assertThrows(UnsupportedOperationException.class, () ->
                entryRepository.deleteById(entryId)
        );

        assertTrue(ex.getMessage().contains("immutable and cannot be deleted"));
    }

    @Test
    @DisplayName("Attempted ledger transaction deletion via Repository is blocked by immutability enforcement")
    void testAttemptedLedgerTransactionDeletion_viaRepository_isRejected() {
        List<LedgerEntry> entries = entryRepository.findByAccountIdOrderByPostedAtDesc(testAccount.getId());
        LedgerTransaction tx = entries.get(0).getTransaction();

        Exception ex = assertThrows(UnsupportedOperationException.class, () ->
                transactionRepository.delete(tx)
        );

        assertTrue(ex.getMessage().contains("immutable and cannot be deleted"));
    }

    @Test
    @DisplayName("Attempted ledger entry update is ignored by Hibernate @Immutable, preserving original database values")
    void testAttemptedLedgerEntryUpdate_isIgnoredByHibernateImmutable() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        List<LedgerEntry> entries = entryRepository.findByAccountIdOrderByPostedAtDesc(testAccount.getId());
        LedgerEntry entry = entries.get(0);
        BigDecimal originalAmount = entry.getAmount();

        template.execute(status -> {
            LedgerEntry loaded = entityManager.find(LedgerEntry.class, entry.getId());
            loaded.setAmount(new BigDecimal("9999.00"));
            entityManager.flush();
            return null;
        });

        // Clear session and reload from MySQL
        entityManager.clear();
        LedgerEntry reloaded = entryRepository.findById(entry.getId()).orElseThrow();
        assertEquals(0, originalAmount.compareTo(reloaded.getAmount()), "Ledger entry amount in MySQL must remain unchanged due to @Immutable");
    }

    @Test
    @DisplayName("Attempted ledger entry removal via EntityManager triggers onPreRemove and throws UnsupportedOperationException")
    void testAttemptedLedgerEntryRemoval_viaEntityManager_isRejected() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        assertThrows(Exception.class, () -> {
            template.execute(status -> {
                List<LedgerEntry> entries = entryRepository.findByAccountIdOrderByPostedAtDesc(testAccount.getId());
                LedgerEntry entry = entityManager.find(LedgerEntry.class, entries.get(0).getId());
                entityManager.remove(entry);
                entityManager.flush();
                return null;
            });
        });
    }
}
