package com.ledgerline.controller;

import com.ledgerline.dto.AccountResponse;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
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
}

