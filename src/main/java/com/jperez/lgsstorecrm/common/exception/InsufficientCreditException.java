package com.jperez.lgsstorecrm.common.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientCreditException extends RuntimeException {

    public InsufficientCreditException(UUID customerId, BigDecimal requestedAmount, BigDecimal currentBalance) {
        super("Customer " + customerId + " has insufficient credit. Requested: " + requestedAmount
                + ", available: " + currentBalance);
    }
}
