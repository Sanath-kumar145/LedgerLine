package com.ledgerline.controller;

import com.ledgerline.dto.AccountReconciliationResponse;
import com.ledgerline.dto.AccountResponse;
import com.ledgerline.dto.AccountStatementResponse;
import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.TransactionResponse;
import com.ledgerline.dto.WithdrawalRequest;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.AccountService;
import com.ledgerline.service.ReconciliationService;
import com.ledgerline.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final ReconciliationService reconciliationService;

    public AccountController(AccountService accountService,
                             TransactionService transactionService,
                             ReconciliationService reconciliationService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyAccounts(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<AccountResponse> accounts = accountService.getAccountsForCurrentUser(currentUser);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long accountId,
                                                          @AuthenticationPrincipal UserPrincipal currentUser) {
        AccountResponse response = accountService.getAccountById(accountId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{accountId}/deposits")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable Long accountId,
            @Valid @RequestBody DepositRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest servletRequest) {
        TransactionResponse response = transactionService.deposit(
                accountId, request, currentUser, servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{accountId}/withdrawals")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable Long accountId,
            @Valid @RequestBody WithdrawalRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest servletRequest) {
        TransactionResponse response = transactionService.withdraw(
                accountId, request, currentUser, servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{accountId}/statement")
    public ResponseEntity<AccountStatementResponse> getStatement(
            @PathVariable Long accountId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        AccountStatementResponse response = transactionService.getAccountStatement(accountId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountId}/reconciliation")
    public ResponseEntity<AccountReconciliationResponse> getAccountReconciliation(
            @PathVariable Long accountId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        AccountReconciliationResponse response = reconciliationService.reconcileAccount(accountId, currentUser);
        return ResponseEntity.ok(response);
    }
}

