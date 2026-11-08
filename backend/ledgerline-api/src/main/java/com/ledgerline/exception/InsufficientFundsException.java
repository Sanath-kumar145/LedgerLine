package com.ledgerline.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    private final BigDecimal attemptedAmount;
    private final BigDecimal availableBalance;

    public InsufficientFundsException(String message) {
        super(message);
        this.attemptedAmount = null;
        this.availableBalance = null;
    }

    public InsufficientFundsException(BigDecimal attemptedAmount, BigDecimal availableBalance) {
        super(String.format("Insufficient funds: attempted withdrawal of %s exceeds available balance of %s",
                attemptedAmount, availableBalance));
        this.attemptedAmount = attemptedAmount;
        this.availableBalance = availableBalance;
    }

    public BigDecimal getAttemptedAmount() {
        return attemptedAmount;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }
}
