package com.ledgerline.controller;

import com.ledgerline.dto.AccountResponse;
import com.ledgerline.entity.AccountStatus;
import com.ledgerline.entity.AccountType;
import com.ledgerline.exception.ResourceNotFoundException;
import com.ledgerline.security.CustomUserDetailsService;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.security.jwt.JwtTokenProvider;
import com.ledgerline.service.AccountService;
import com.ledgerline.service.ReconciliationService;
import com.ledgerline.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import com.ledgerline.config.JacksonConfig;
import com.ledgerline.config.SecurityConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountController.class)
@AutoConfigureMockMvc
@Import({JacksonConfig.class, SecurityConfig.class})
@DisplayName("Account Ownership Authorization Tests")
class AccountOwnershipTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private ReconciliationService reconciliationService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @WithMockUser(username = "alice", roles = {"ACCOUNT_HOLDER"})
    @DisplayName("Account holder can access their own account details")
    void testAccountHolderCanAccessOwnAccount() throws Exception {
        Long accountId = 101L;
        AccountResponse response = new AccountResponse(
                accountId,
                "LL-US-8921-100001",
                1L, // Owned by Alice (ID 1)
                AccountType.CHECKING,
                "USD",
                new BigDecimal("500.00"),
                AccountStatus.ACTIVE,
                Instant.now()
        );

        when(accountService.getAccountById(eq(accountId), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/accounts/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId))
                .andExpect(jsonPath("$.accountNumber").value("LL-US-8921-100001"))
                .andExpect(jsonPath("$.balance").value(500.00));
    }

    @Test
    @WithMockUser(username = "alice", roles = {"ACCOUNT_HOLDER"})
    @DisplayName("Account holder is rejected with 403 Forbidden when requesting another user's account")
    void testAccountHolderCannotAccessAnotherUsersAccount() throws Exception {
        Long bobAccountId = 202L;

        when(accountService.getAccountById(eq(bobAccountId), any()))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this account."));

        mockMvc.perform(get("/api/v1/accounts/{accountId}", bobAccountId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Access denied: You do not own this account."));
    }

    @Test
    @WithMockUser(username = "alice", roles = {"ACCOUNT_HOLDER"})
    @DisplayName("Requesting non-existent account returns 404 Not Found")
    void testNonExistentAccountReturns404() throws Exception {
        Long invalidId = 9999L;

        when(accountService.getAccountById(eq(invalidId), any()))
                .thenThrow(new ResourceNotFoundException("Account not found with id: " + invalidId));

        mockMvc.perform(get("/api/v1/accounts/{accountId}", invalidId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Unauthenticated user accessing accounts returns 401 Unauthorized")
    void testAnonymousAccessRejected() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/101"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice", roles = {"ACCOUNT_HOLDER"})
    @DisplayName("Account holder can list all their owned accounts")
    void testListOwnedAccounts() throws Exception {
        AccountResponse acc = new AccountResponse(
                101L, "LL-US-8921-100001", 1L, AccountType.CHECKING, "USD",
                new BigDecimal("500.00"), AccountStatus.ACTIVE, Instant.now()
        );

        when(accountService.getAccountsForCurrentUser(any())).thenReturn(List.of(acc));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].accountNumber").value("LL-US-8921-100001"));
    }
}

