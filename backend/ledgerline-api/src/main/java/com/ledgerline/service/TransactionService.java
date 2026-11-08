package com.ledgerline.service;

import com.ledgerline.dto.AccountStatementResponse;
import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.TransactionResponse;
import com.ledgerline.dto.WithdrawalRequest;
import com.ledgerline.security.UserPrincipal;

public interface TransactionService {

    TransactionResponse deposit(Long accountId, DepositRequest request, UserPrincipal currentUser, String ipAddress);

    TransactionResponse withdraw(Long accountId, WithdrawalRequest request, UserPrincipal currentUser, String ipAddress);

    AccountStatementResponse getAccountStatement(Long accountId, UserPrincipal currentUser);
}

