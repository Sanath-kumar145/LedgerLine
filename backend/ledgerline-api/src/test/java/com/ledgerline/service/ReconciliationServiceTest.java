package com.ledgerline.service;

import com.ledgerline.dto.AccountReconciliationResponse;
import com.ledgerline.dto.SystemReconciliationResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountType;
import com.ledgerline.entity.EntryType;
import com.ledgerline.entity.TransactionStatus;
import com.ledgerline.entity.UserStatus;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.AuditEventRepository;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.repository.LedgerTransactionRepository;
import com.ledgerline.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Reconciliation Service Unit Tests — Financial Integrity & Conservation of Money")
class ReconciliationServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerTransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository entryRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ReconciliationServiceImpl reconciliationService;

    private UserPrincipal customerUser;
    private UserPrincipal auditorUser;
    private UserPrincipal unauthorizedUser;
    private Account customerAccount;

    @BeforeEach
    void setUp() {
        customerUser = new UserPrincipal(
                1L, "alice", "alice@example.com", "Alice", "pw",
                UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );
        auditorUser = new UserPrincipal(
                99L, "auditor", "auditor@example.com", "Auditor", "pw",
                UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"))
        );
        unauthorizedUser = new UserPrincipal(
                2L, "bob", "bob@example.com", "Bob", "pw",
                UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        customerAccount = new Account("LL-IN-TEST-1001", 1L, AccountType.CHECKING, "INR", new BigDecimal("1000.00"));
        customerAccount.setId(10L);
    }

    @Test
    @DisplayName("Account reconciliation returns isReconciled=true when sum of entries equals materialized balance")
    void testReconcileAccount_balanced() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(customerAccount));
        when(entryRepository.sumAmountByAccountIdAndEntryTypeAndStatus(10L, EntryType.CREDIT, TransactionStatus.POSTED))
                .thenReturn(new BigDecimal("1500.00"));
        when(entryRepository.sumAmountByAccountIdAndEntryTypeAndStatus(10L, EntryType.DEBIT, TransactionStatus.POSTED))
                .thenReturn(new BigDecimal("500.00"));

        AccountReconciliationResponse response = reconciliationService.reconcileAccount(10L, customerUser);

        assertNotNull(response);
        assertTrue(response.isReconciled());
        assertEquals(new BigDecimal("1000.00"), response.getMaterializedBalance());
        assertEquals(new BigDecimal("1000.00"), response.getComputedLedgerBalance());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getDiscrepancy()));
    }

    @Test
    @DisplayName("Account reconciliation returns isReconciled=false when ledger math does not match balance")
    void testReconcileAccount_discrepancy() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(customerAccount));
        // credits 1200 - debits 500 = 700 != materialized 1000
        when(entryRepository.sumAmountByAccountIdAndEntryTypeAndStatus(10L, EntryType.CREDIT, TransactionStatus.POSTED))
                .thenReturn(new BigDecimal("1200.00"));
        when(entryRepository.sumAmountByAccountIdAndEntryTypeAndStatus(10L, EntryType.DEBIT, TransactionStatus.POSTED))
                .thenReturn(new BigDecimal("500.00"));

        AccountReconciliationResponse response = reconciliationService.reconcileAccount(10L, customerUser);

        assertNotNull(response);
        assertFalse(response.isReconciled());
        assertEquals(new BigDecimal("1000.00"), response.getMaterializedBalance());
        assertEquals(new BigDecimal("700.00"), response.getComputedLedgerBalance());
        assertEquals(new BigDecimal("300.00"), response.getDiscrepancy());
    }

    @Test
    @DisplayName("Account reconciliation denied for unauthorized non-owner")
    void testReconcileAccount_accessDenied() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(customerAccount));

        assertThrows(AccessDeniedException.class, () ->
                reconciliationService.reconcileAccount(10L, unauthorizedUser)
        );
    }

    @Test
    @DisplayName("System reconciliation returns SYSTEM_BALANCED when all debits equal all credits and no unbalanced transactions")
    void testReconcileSystem_balanced() {
        when(entryRepository.sumTotalAmountByEntryTypeAndStatus(EntryType.DEBIT, TransactionStatus.POSTED))
                .thenReturn(new BigDecimal("50000.00"));
        when(entryRepository.sumTotalAmountByEntryTypeAndStatus(EntryType.CREDIT, TransactionStatus.POSTED))
                .thenReturn(new BigDecimal("50000.00"));
        when(transactionRepository.findUnbalancedTransactionIdsByStatus(TransactionStatus.POSTED))
                .thenReturn(Collections.emptyList());
        when(accountRepository.count()).thenReturn(10L);
        when(transactionRepository.count()).thenReturn(100L);
        when(entryRepository.count()).thenReturn(200L);

        SystemReconciliationResponse response = reconciliationService.reconcileSystem(auditorUser);

        assertNotNull(response);
        assertEquals("SYSTEM_BALANCED", response.getIntegrityStatus());
        assertEquals(new BigDecimal("50000.00"), response.getSystemTotalDebits());
        assertEquals(new BigDecimal("50000.00"), response.getSystemTotalCredits());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getSystemDelta()));
        assertEquals(0, response.getUnbalancedTransactionCount());
    }

    @Test
    @DisplayName("System reconciliation detects SYSTEM_CORRUPTED when debits != credits or unbalanced tx exist")
    void testReconcileSystem_corrupted() {
        when(entryRepository.sumTotalAmountByEntryTypeAndStatus(EntryType.DEBIT, TransactionStatus.POSTED))
                .thenReturn(new BigDecimal("50000.00"));
        when(entryRepository.sumTotalAmountByEntryTypeAndStatus(EntryType.CREDIT, TransactionStatus.POSTED))
                .thenReturn(new BigDecimal("49900.00"));
        when(transactionRepository.findUnbalancedTransactionIdsByStatus(TransactionStatus.POSTED))
                .thenReturn(List.of(123L));
        when(accountRepository.count()).thenReturn(10L);
        when(transactionRepository.count()).thenReturn(100L);
        when(entryRepository.count()).thenReturn(200L);

        SystemReconciliationResponse response = reconciliationService.reconcileSystem(auditorUser);

        assertNotNull(response);
        assertEquals("SYSTEM_CORRUPTED", response.getIntegrityStatus());
        assertEquals(new BigDecimal("100.00"), response.getSystemDelta());
        assertEquals(1, response.getUnbalancedTransactionCount());
    }

    @Test
    @DisplayName("System reconciliation denied for non-auditor / non-admin")
    void testReconcileSystem_deniedForRegularUser() {
        assertThrows(AccessDeniedException.class, () ->
                reconciliationService.reconcileSystem(customerUser)
        );
    }
}
