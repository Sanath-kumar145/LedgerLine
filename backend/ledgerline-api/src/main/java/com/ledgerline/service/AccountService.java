package com.ledgerline.service;

import com.ledgerline.dto.AccountResponse;
import com.ledgerline.security.UserPrincipal;
import java.util.List;

public interface AccountService {
    AccountResponse getAccountById(Long accountId, UserPrincipal currentUser);
    List<AccountResponse> getAccountsForCurrentUser(UserPrincipal currentUser);
}

