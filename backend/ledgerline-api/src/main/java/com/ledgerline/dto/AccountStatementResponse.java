package com.ledgerline.dto;

import java.math.BigDecimal;
import java.util.List;

public class AccountStatementResponse {

    private Long accountId;
    private String accountNumber;
    private String currency;
    private BigDecimal currentBalance;
    private int totalEntries;
    private List<LedgerEntryDto> entries;

    public AccountStatementResponse() {
    }

    public AccountStatementResponse(Long accountId,
                                    String accountNumber,
                                    String currency,
                                    BigDecimal currentBalance,
                                    int totalEntries,
                                    List<LedgerEntryDto> entries) {
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.currency = currency;
        this.currentBalance = currentBalance;
        this.totalEntries = totalEntries;
        this.entries = entries;
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

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public int getTotalEntries() {
        return totalEntries;
    }

    public List<LedgerEntryDto> getEntries() {
        return entries;
    }
}

