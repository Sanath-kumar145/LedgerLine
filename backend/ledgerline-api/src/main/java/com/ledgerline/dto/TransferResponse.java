package com.ledgerline.dto;

import com.ledgerline.entity.TransactionStatus;
import com.ledgerline.entity.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class TransferResponse {

    private String transactionReference;
    private TransactionType transactionType;
    private Long sourceAccountId;
    private Long destinationAccountId;
    private BigDecimal amount;
    private String currency;
    private BigDecimal sourceBalanceAfter;
    private BigDecimal destinationBalanceAfter;
    private TransactionStatus status;
    private String description;
    private String idempotencyKey;
    private boolean replayed;
    private Instant timestamp;
    private List<LedgerEntryDto> entries;

    public TransferResponse() {
    }

    public TransferResponse(String transactionReference,
                            TransactionType transactionType,
                            Long sourceAccountId,
                            Long destinationAccountId,
                            BigDecimal amount,
                            String currency,
                            BigDecimal sourceBalanceAfter,
                            BigDecimal destinationBalanceAfter,
                            TransactionStatus status,
                            String description,
                            String idempotencyKey,
                            boolean replayed,
                            Instant timestamp,
                            List<LedgerEntryDto> entries) {
        this.transactionReference = transactionReference;
        this.transactionType = transactionType;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.currency = currency;
        this.sourceBalanceAfter = sourceBalanceAfter;
        this.destinationBalanceAfter = destinationBalanceAfter;
        this.status = status;
        this.description = description;
        this.idempotencyKey = idempotencyKey;
        this.replayed = replayed;
        this.timestamp = timestamp;
        this.entries = entries;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(Long sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public Long getDestinationAccountId() {
        return destinationAccountId;
    }

    public void setDestinationAccountId(Long destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getSourceBalanceAfter() {
        return sourceBalanceAfter;
    }

    public void setSourceBalanceAfter(BigDecimal sourceBalanceAfter) {
        this.sourceBalanceAfter = sourceBalanceAfter;
    }

    public BigDecimal getDestinationBalanceAfter() {
        return destinationBalanceAfter;
    }

    public void setDestinationBalanceAfter(BigDecimal destinationBalanceAfter) {
        this.destinationBalanceAfter = destinationBalanceAfter;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public boolean isReplayed() {
        return replayed;
    }

    public void setReplayed(boolean replayed) {
        this.replayed = replayed;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public List<LedgerEntryDto> getEntries() {
        return entries;
    }

    public void setEntries(List<LedgerEntryDto> entries) {
        this.entries = entries;
    }
}

