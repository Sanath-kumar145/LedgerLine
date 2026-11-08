package com.ledgerline.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class SystemReconciliationResponse {

    private long totalAccounts;
    private long totalTransactions;
    private long totalEntries;
    private BigDecimal systemTotalDebits;
    private BigDecimal systemTotalCredits;
    private BigDecimal systemDelta;
    private int unbalancedTransactionCount;
    private String integrityStatus;
    private Instant evaluatedAt;

    public SystemReconciliationResponse() {
    }

    public SystemReconciliationResponse(long totalAccounts,
                                        long totalTransactions,
                                        long totalEntries,
                                        BigDecimal systemTotalDebits,
                                        BigDecimal systemTotalCredits,
                                        BigDecimal systemDelta,
                                        int unbalancedTransactionCount,
                                        String integrityStatus,
                                        Instant evaluatedAt) {
        this.totalAccounts = totalAccounts;
        this.totalTransactions = totalTransactions;
        this.totalEntries = totalEntries;
        this.systemTotalDebits = systemTotalDebits;
        this.systemTotalCredits = systemTotalCredits;
        this.systemDelta = systemDelta;
        this.unbalancedTransactionCount = unbalancedTransactionCount;
        this.integrityStatus = integrityStatus;
        this.evaluatedAt = evaluatedAt;
    }

    public long getTotalAccounts() {
        return totalAccounts;
    }

    public long getTotalTransactions() {
        return totalTransactions;
    }

    public long getTotalEntries() {
        return totalEntries;
    }

    public BigDecimal getSystemTotalDebits() {
        return systemTotalDebits;
    }

    public BigDecimal getSystemTotalCredits() {
        return systemTotalCredits;
    }

    public BigDecimal getSystemDelta() {
        return systemDelta;
    }

    public int getUnbalancedTransactionCount() {
        return unbalancedTransactionCount;
    }

    public String getIntegrityStatus() {
        return integrityStatus;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }
}

