package com.ledgerline.service;

import com.ledgerline.dto.AccountResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountStatus;
import com.ledgerline.entity.AccountType;
import com.ledgerline.entity.UserStatus;
import com.ledgerline.exception.ResourceNotFoundException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account Ownership Service Logic Tests")
class AccountOwnershipServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountServiceImpl accountService;

    private UserPrincipal alicePrincipal;
    private UserPrincipal bobPrincipal;
    private UserPrincipal adminPrincipal;
    private UserPrincipal auditorPrincipal;

    private Account aliceAccount;
    private Account bobAccount;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(accountRepository);

        alicePrincipal = new UserPrincipal(
                1L, "alice", "alice@test.com", "Alice Smith", "hash",
                UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        bobPrincipal = new UserPrincipal(
                2L, "bob", "bob@test.com", "Bob Jones", "hash",
                UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        adminPrincipal = new UserPrincipal(
                99L, "admin", "admin@test.com", "Admin User", "hash",
                UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        auditorPrincipal = new UserPrincipal(
                100L, "auditor", "auditor@test.com", "Auditor User", "hash",
                UserStatus.ACTIVE, List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"))
        );

        aliceAccount = new Account("LL-US-ALICE-01", 1L, AccountType.CHECKING, "USD", new BigDecimal("1000.00"));
        aliceAccount.setId(101L);

        bobAccount = new Account("LL-US-BOB-01", 2L, AccountType.CHECKING, "USD", new BigDecimal("500.00"));
        bobAccount.setId(202L);
    }

    @Test
    @DisplayName("Alice can successfully read her own account")
    void testAliceAccessesOwnAccount() {
        when(accountRepository.findById(101L)).thenReturn(Optional.of(aliceAccount));

        AccountResponse response = accountService.getAccountById(101L, alicePrincipal);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals(1L, response.getUserId());
        assertEquals("LL-US-ALICE-01", response.getAccountNumber());
    }

    @Test
    @DisplayName("Alice is blocked with AccessDeniedException when trying to read Bob's account")
    void testAliceBlockedFromBobsAccount() {
        when(accountRepository.findById(202L)).thenReturn(Optional.of(bobAccount));

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () ->
                accountService.getAccountById(202L, alicePrincipal)
        );

        assertEquals("Access denied: You do not own this account.", exception.getMessage());
    }

    @Test
    @DisplayName("Bob is blocked with AccessDeniedException when trying to read Alice's account")
    void testBobBlockedFromAlicesAccount() {
        when(accountRepository.findById(101L)).thenReturn(Optional.of(aliceAccount));

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () ->
                accountService.getAccountById(101L, bobPrincipal)
        );

        assertEquals("Access denied: You do not own this account.", exception.getMessage());
    }

    @Test
    @DisplayName("Admin can inspect any customer account for platform operations")
    void testAdminCanAccessAnyAccount() {
        when(accountRepository.findById(202L)).thenReturn(Optional.of(bobAccount));

        AccountResponse response = accountService.getAccountById(202L, adminPrincipal);

        assertNotNull(response);
        assertEquals(202L, response.getId());
    }

    @Test
    @DisplayName("Auditor can inspect any customer account for regulatory review")
    void testAuditorCanAccessAnyAccount() {
        when(accountRepository.findById(101L)).thenReturn(Optional.of(aliceAccount));

        AccountResponse response = accountService.getAccountById(101L, auditorPrincipal);

        assertNotNull(response);
        assertEquals(101L, response.getId());
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when account does not exist")
    void testAccountNotFound() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                accountService.getAccountById(999L, alicePrincipal)
        );
    }

    @Test
    @DisplayName("getAccountsForCurrentUser uses authenticated principal ID, never trusting client parameters")
    void testListUsesAuthenticatedPrincipal() {
        when(accountRepository.findByUserId(1L)).thenReturn(List.of(aliceAccount));

        List<AccountResponse> accounts = accountService.getAccountsForCurrentUser(alicePrincipal);

        assertNotNull(accounts);
        assertEquals(1, accounts.size());
        assertEquals(101L, accounts.get(0).getId());
    }
}

