package com.jperez.lgsstorecrm.transaction.event;

import com.jperez.lgsstorecrm.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreditTransactionAppliedEvent(
        UUID tenantId,
        UUID customerId,
        UUID employeeId,
        UUID transactionId,
        TransactionType type,
        BigDecimal amount,
        BigDecimal resultingBalance,
        LocalDateTime occurredAt
) {
}
