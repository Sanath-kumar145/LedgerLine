package com.ledgerline.dto;

import com.ledgerline.entity.TransactionStatus;
import com.ledgerline.entity.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public class TransactionResponse {

    private String transactionReference;
    private Long accountId;
    private String accountNumber;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String currency;
    private BigDecimal newBalance;
    private TransactionStatus status;
    private String description;
    private Instant timestamp;

    public TransactionResponse() {
    }

    public TransactionResponse(String transactionReference,
                               Long accountId,
                               String accountNumber,
                               TransactionType transactionType,
                               BigDecimal amount,
                               String currency,
                               BigDecimal newBalance,
                               TransactionStatus status,
                               String description,
                               Instant timestamp) {
        this.transactionReference = transactionReference;
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
        this.newBalance = newBalance;
        this.status = status;
        this.description = description;
        this.timestamp = timestamp;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getNewBalance() {
        return newBalance;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}

