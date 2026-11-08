package com.ledgerline.controller;

import com.ledgerline.dto.AccountReconciliationResponse;
import com.ledgerline.dto.SystemReconciliationResponse;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.ReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auditor")
@PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
public class AuditorController {

    private final ReconciliationService reconciliationService;

    public AuditorController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/reconcile/{accountId}")
    public ResponseEntity<AccountReconciliationResponse> reconcileAccount(
            @PathVariable Long accountId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        AccountReconciliationResponse response = reconciliationService.reconcileAccount(accountId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/system-reconciliation")
    public ResponseEntity<SystemReconciliationResponse> reconcileSystem(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        SystemReconciliationResponse response = reconciliationService.reconcileSystem(currentUser);
        return ResponseEntity.ok(response);
    }
}

