package com.ledgerline.service;

import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.TransferResponse;
import com.ledgerline.security.UserPrincipal;

public interface TransferService {

    TransferResponse transfer(TransferRequest request, String idempotencyKey, UserPrincipal currentUser, String ipAddress);
}

