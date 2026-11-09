package com.ledgerline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.config.JacksonConfig;
import com.ledgerline.config.SecurityConfig;
import com.ledgerline.dto.LedgerEntryDto;
import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.TransferResponse;
import com.ledgerline.entity.EntryType;
import com.ledgerline.entity.TransactionStatus;
import com.ledgerline.entity.TransactionType;
import com.ledgerline.exception.IdempotencyConflictException;
import com.ledgerline.exception.InsufficientFundsException;
import com.ledgerline.security.CustomUserDetailsService;
import com.ledgerline.security.jwt.JwtTokenProvider;
import com.ledgerline.service.TransferService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {TransferController.class})
@AutoConfigureMockMvc
@Import({JacksonConfig.class, SecurityConfig.class})
@DisplayName("Transfer Controller WebMvc Slice Tests — Validation, HTTP Responses & Idempotency Header")
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransferService transferService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser(username = "alice", roles = "ACCOUNT_HOLDER")
    @DisplayName("POST /api/v1/transfers with valid request and new Idempotency-Key returns HTTP 201 Created")
    void testSuccessfulTransfer_returns201() throws Exception {
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("500.00"), "Rent payment");

        TransferResponse mockResponse = new TransferResponse(
                "TX-XFER-9988",
                TransactionType.TRANSFER,
                10L,
                20L,
                new BigDecimal("500.00"),
                "INR",
                new BigDecimal("4500.00"),
                new BigDecimal("2500.00"),
                TransactionStatus.POSTED,
                "Rent payment",
                "xfer-key-101",
                false,
                Instant.now(),
                List.of(
                        new LedgerEntryDto(1L, "TX-XFER-9988", EntryType.DEBIT, new BigDecimal("500.00"), new BigDecimal("4500.00"), Instant.now(), "Rent payment"),
                        new LedgerEntryDto(2L, "TX-XFER-9988", EntryType.CREDIT, new BigDecimal("500.00"), new BigDecimal("2500.00"), Instant.now(), "Rent payment")
                )
        );

        when(transferService.transfer(any(TransferRequest.class), eq("xfer-key-101"), any(), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "xfer-key-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionReference").value("TX-XFER-9988"))
                .andExpect(jsonPath("$.sourceAccountId").value(10))
                .andExpect(jsonPath("$.destinationAccountId").value(20))
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.status").value("POSTED"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "ACCOUNT_HOLDER")
    @DisplayName("POST /api/v1/transfers on replayed duplicate returns HTTP 200 OK with replayed=true")
    void testReplayedTransfer_returns200() throws Exception {
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("500.00"), "Rent payment");

        TransferResponse replayedResponse = new TransferResponse(
                "TX-XFER-9988",
                TransactionType.TRANSFER,
                10L,
                20L,
                new BigDecimal("500.00"),
                "INR",
                new BigDecimal("4500.00"),
                new BigDecimal("2500.00"),
                TransactionStatus.POSTED,
                "Rent payment",
                "xfer-key-101",
                true,
                Instant.now(),
                List.of()
        );

        when(transferService.transfer(any(TransferRequest.class), eq("xfer-key-101"), any(), any()))
                .thenReturn(replayedResponse);

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "xfer-key-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionReference").value("TX-XFER-9988"))
                .andExpect(jsonPath("$.replayed").value(true));
    }

    @Test
    @WithMockUser(username = "alice", roles = "ACCOUNT_HOLDER")
    @DisplayName("POST /api/transfers alias route works identically to /api/v1/transfers")
    void testTransferAliasRoute_returns201() throws Exception {
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("250.00"), "Alias test");

        TransferResponse mockResponse = new TransferResponse(
                "TX-ALIAS-77",
                TransactionType.TRANSFER,
                10L,
                20L,
                new BigDecimal("250.00"),
                "INR",
                new BigDecimal("4750.00"),
                new BigDecimal("2250.00"),
                TransactionStatus.POSTED,
                "Alias test",
                "xfer-key-alias",
                false,
                Instant.now(),
                List.of()
        );

        when(transferService.transfer(any(TransferRequest.class), eq("xfer-key-alias"), any(), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/transfers")
                        .header("Idempotency-Key", "xfer-key-alias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionReference").value("TX-ALIAS-77"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "ACCOUNT_HOLDER")
    @DisplayName("POST /api/v1/transfers missing required Idempotency-Key header returns HTTP 400 Bad Request")
    void testMissingIdempotencyKeyHeader_returns400() throws Exception {
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("500.00"), "Rent payment");

        when(transferService.transfer(any(), eq(null), any(), any()))
                .thenThrow(new com.ledgerline.exception.InvalidTransactionException("Idempotency-Key header is required for transfers."));

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Idempotency-Key header is required for transfers."));
    }

    @Test
    @WithMockUser(username = "alice", roles = "ACCOUNT_HOLDER")
    @DisplayName("POST /api/v1/transfers with negative amount returns HTTP 400 Bad Request")
    void testNegativeAmount_returns400() throws Exception {
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("-50.00"), "Negative");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "key-neg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    @WithMockUser(username = "alice", roles = "ACCOUNT_HOLDER")
    @DisplayName("POST /api/v1/transfers with zero amount returns HTTP 400 Bad Request")
    void testZeroAmount_returns400() throws Exception {
        TransferRequest request = new TransferRequest(10L, 20L, BigDecimal.ZERO, "Zero");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "key-zero")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    @WithMockUser(username = "alice", roles = "ACCOUNT_HOLDER")
    @DisplayName("POST /api/v1/transfers with scale > 2 returns HTTP 400 Bad Request")
    void testInvalidScale_returns400() throws Exception {
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("10.555"), "Three decimals");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "key-scale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    @WithMockUser(username = "alice", roles = "ACCOUNT_HOLDER")
    @DisplayName("POST /api/v1/transfers with insufficient balance returns RFC 7807 HTTP 422 Unprocessable Entity")
    void testInsufficientFunds_returns422() throws Exception {
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("9999.00"), "Excess");

        when(transferService.transfer(any(), eq("key-insuf"), any(), any()))
                .thenThrow(new InsufficientFundsException(new BigDecimal("9999.00"), new BigDecimal("100.00")));

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "key-insuf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Unprocessable Entity"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "ACCOUNT_HOLDER")
    @DisplayName("POST /api/v1/transfers with conflicting payload on existing key returns HTTP 409 Conflict")
    void testIdempotencyConflict_returns409() throws Exception {
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("600.00"), "Altered payload");

        when(transferService.transfer(any(), eq("key-conflict"), any(), any()))
                .thenThrow(new IdempotencyConflictException("Idempotency key 'key-conflict' has already been used with a different request payload.", "key-conflict"));

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "key-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Idempotency Key Conflict"));
    }
}

