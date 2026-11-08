package com.ledgerline.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class AccountReconciliationResponse {

    private Long accountId;
    private String accountNumber;
    private String currency;
    private BigDecimal materializedBalance;
    private BigDecimal totalCreditSum;
    private BigDecimal totalDebitSum;
    private BigDecimal computedLedgerBalance;
    private BigDecimal discrepancy;
    private boolean isReconciled;
    private Instant verifiedAt;

    public AccountReconciliationResponse() {
    }

    public AccountReconciliationResponse(Long accountId,
                                         String accountNumber,
                                         String currency,
                                         BigDecimal materializedBalance,
                                         BigDecimal totalCreditSum,
                                         BigDecimal totalDebitSum,
                                         BigDecimal computedLedgerBalance,
                                         BigDecimal discrepancy,
                                         boolean isReconciled,
                                         Instant verifiedAt) {
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.currency = currency;
        this.materializedBalance = materializedBalance;
        this.totalCreditSum = totalCreditSum;
        this.totalDebitSum = totalDebitSum;
        this.computedLedgerBalance = computedLedgerBalance;
        this.discrepancy = discrepancy;
        this.isReconciled = isReconciled;
        this.verifiedAt = verifiedAt;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getMaterializedBalance() {
        return materializedBalance;
    }

    public BigDecimal getTotalCreditSum() {
        return totalCreditSum;
    }

    public BigDecimal getTotalDebitSum() {
        return totalDebitSum;
    }

    public BigDecimal getComputedLedgerBalance() {
        return computedLedgerBalance;
    }

    public BigDecimal getDiscrepancy() {
        return discrepancy;
    }

    public boolean isReconciled() {
        return isReconciled;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}

