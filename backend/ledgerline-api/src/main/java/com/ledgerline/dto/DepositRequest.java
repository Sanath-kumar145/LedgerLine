package com.ledgerline.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class DepositRequest {

    @NotNull(message = "Deposit amount is required.")
    @DecimalMin(value = "0.01", message = "Deposit amount must be strictly greater than zero.")
    @Digits(integer = 17, fraction = 2, message = "Deposit amount cannot exceed two decimal places.")
    private BigDecimal amount;

    private String description;

    public DepositRequest() {
    }

    public DepositRequest(BigDecimal amount, String description) {
        this.amount = amount;
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

