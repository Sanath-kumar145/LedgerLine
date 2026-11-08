package com.ledgerline.service;

import com.ledgerline.dto.AccountReconciliationResponse;
import com.ledgerline.dto.SystemReconciliationResponse;
import com.ledgerline.security.UserPrincipal;

public interface ReconciliationService {

    AccountReconciliationResponse reconcileAccount(Long accountId, UserPrincipal currentUser);

    SystemReconciliationResponse reconcileSystem(UserPrincipal currentUser);
}

