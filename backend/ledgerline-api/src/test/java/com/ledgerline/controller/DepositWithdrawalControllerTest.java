package com.ledgerline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.config.JacksonConfig;
import com.ledgerline.config.SecurityConfig;
import com.ledgerline.dto.AccountStatementResponse;
import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.SystemReconciliationResponse;
import com.ledgerline.dto.TransactionResponse;
import com.ledgerline.dto.WithdrawalRequest;
import com.ledgerline.entity.TransactionStatus;
import com.ledgerline.entity.TransactionType;
import com.ledgerline.exception.InsufficientFundsException;
import com.ledgerline.security.CustomUserDetailsService;
import com.ledgerline.security.jwt.JwtTokenProvider;
import com.ledgerline.service.AccountService;
import com.ledgerline.service.ReconciliationService;
import com.ledgerline.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AccountController.class, AuditorController.class})
@AutoConfigureMockMvc
@Import({JacksonConfig.class, SecurityConfig.class})
@DisplayName("Deposit, Withdrawal & Auditor Controller WebMvc Slice Tests")
class DepositWithdrawalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    @DisplayName("POST /accounts/{id}/deposits returns 201 Created on valid request")
    void testDeposit_success() throws Exception {
        DepositRequest request = new DepositRequest(new BigDecimal("500.00"), "Salary deposit");
        TransactionResponse response = new TransactionResponse(
                "TX-REF-101", 10L, "LL-IN-TEST-1001", TransactionType.DEPOSIT,
                new BigDecimal("500.00"), "INR", new BigDecimal("1500.00"),
                TransactionStatus.POSTED, "Salary deposit", Instant.now()
        );

        when(transactionService.deposit(eq(10L), any(DepositRequest.class), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts/10/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionReference").value("TX-REF-101"))
                .andExpect(jsonPath("$.transactionType").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.amount").value(500.00));
    }

    @Test
    @WithMockUser(username = "alice", roles = {"ACCOUNT_HOLDER"})
    @DisplayName("POST /accounts/{id}/deposits returns 400 Bad Request on negative amount")
    void testDeposit_validationError() throws Exception {
        DepositRequest invalidRequest = new DepositRequest(new BigDecimal("-10.00"), "Negative deposit");

        mockMvc.perform(post("/api/v1/accounts/10/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "alice", roles = {"ACCOUNT_HOLDER"})
    @DisplayName("POST /accounts/{id}/withdrawals returns 201 Created on valid request")
    void testWithdrawal_success() throws Exception {
        WithdrawalRequest request = new WithdrawalRequest(new BigDecimal("200.00"), "ATM withdrawal");
        TransactionResponse response = new TransactionResponse(
                "TX-REF-102", 10L, "LL-IN-TEST-1001", TransactionType.WITHDRAWAL,
                new BigDecimal("200.00"), "INR", new BigDecimal("800.00"),
                TransactionStatus.POSTED, "ATM withdrawal", Instant.now()
        );

        when(transactionService.withdraw(eq(10L), any(WithdrawalRequest.class), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts/10/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionReference").value("TX-REF-102"))
                .andExpect(jsonPath("$.transactionType").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.amount").value(200.00));
    }

    @Test
    @WithMockUser(username = "alice", roles = {"ACCOUNT_HOLDER"})
    @DisplayName("POST /accounts/{id}/withdrawals returns 422 Unprocessable Entity when funds are insufficient")
    void testWithdrawal_insufficientFunds() throws Exception {
        WithdrawalRequest request = new WithdrawalRequest(new BigDecimal("9999.00"), "Excessive withdrawal");

        when(transactionService.withdraw(eq(10L), any(WithdrawalRequest.class), any(), any()))
                .thenThrow(new InsufficientFundsException(new BigDecimal("9999.00"), new BigDecimal("100.00")));

        mockMvc.perform(post("/api/v1/accounts/10/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Insufficient funds")));
    }

    @Test
    @WithMockUser(username = "alice", roles = {"ACCOUNT_HOLDER"})
    @DisplayName("GET /accounts/{id}/statement returns 200 OK")
    void testGetStatement() throws Exception {
        AccountStatementResponse response = new AccountStatementResponse(
                10L, "LL-IN-TEST-1001", "INR", new BigDecimal("1000.00"),
                0, Collections.emptyList()
        );

        when(transactionService.getAccountStatement(eq(10L), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/accounts/10/statement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("LL-IN-TEST-1001"))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    @WithMockUser(username = "bob", roles = {"AUDITOR"})
    @DisplayName("GET /auditor/system-reconciliation allows AUDITOR role")
    void testAuditorSystemReconciliation_allowedForAuditor() throws Exception {
        SystemReconciliationResponse response = new SystemReconciliationResponse(
                5L, 20L, 40L, new BigDecimal("5000.00"), new BigDecimal("5000.00"), BigDecimal.ZERO,
                0, "SYSTEM_BALANCED", Instant.now()
        );

        when(reconciliationService.reconcileSystem(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/auditor/system-reconciliation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integrityStatus").value("SYSTEM_BALANCED"));
    }

    @Test
    @WithMockUser(username = "alice", roles = {"ACCOUNT_HOLDER"})
    @DisplayName("GET /auditor/system-reconciliation forbidden (403) for regular user")
    void testAuditorSystemReconciliation_forbiddenForAccountHolder() throws Exception {
        mockMvc.perform(get("/api/v1/auditor/system-reconciliation"))
                .andExpect(status().isForbidden());
    }
}
