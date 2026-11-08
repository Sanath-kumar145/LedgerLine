package com.ledgerline.dto;

import com.ledgerline.entity.EntryType;
import java.math.BigDecimal;
import java.time.Instant;

public class LedgerEntryDto {

    private Long entryId;
    private String transactionReference;
    private EntryType entryType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private Instant postedAt;
    private String description;

    public LedgerEntryDto() {
    }

    public LedgerEntryDto(Long entryId,
                          String transactionReference,
                          EntryType entryType,
                          BigDecimal amount,
                          BigDecimal balanceAfter,
                          Instant postedAt,
                          String description) {
        this.entryId = entryId;
        this.transactionReference = transactionReference;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.postedAt = postedAt;
        this.description = description;
    }

    public Long getEntryId() {
        return entryId;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public String getDescription() {
        return description;
    }
}

