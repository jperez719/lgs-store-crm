package com.jperez.lgsstorecrm.transaction.dto;

import com.jperez.lgsstorecrm.transaction.CreditTransaction;
import com.jperez.lgsstorecrm.transaction.TransactionType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class TransactionResponse {

    private final UUID id;
    private final UUID customerId;
    private final UUID employeeId;
    private final String employeeName;
    private final TransactionType type;
    private final BigDecimal amount;
    private final String reason;
    private final BigDecimal resultingBalance;
    private final LocalDateTime createdAt;

    public TransactionResponse(CreditTransaction transaction) {
        this.id = transaction.getId();
        this.customerId = transaction.getCustomer().getId();
        this.employeeId = transaction.getEmployee() != null ? transaction.getEmployee().getId() : null;
        this.employeeName = transaction.getEmployee() != null
                ? transaction.getEmployee().getFirstName() + " " + transaction.getEmployee().getLastName()
                : null;
        this.type = transaction.getType();
        this.amount = transaction.getAmount();
        this.reason = transaction.getReason();
        this.resultingBalance = transaction.getResultingBalance();
        this.createdAt = transaction.getCreatedAt();
    }
}
