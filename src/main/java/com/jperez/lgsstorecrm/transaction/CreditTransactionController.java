package com.jperez.lgsstorecrm.transaction;

import com.jperez.lgsstorecrm.transaction.dto.CreateTransactionRequest;
import com.jperez.lgsstorecrm.transaction.dto.TransactionResponse;
import com.jperez.lgsstorecrm.customer.dto.CustomerResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers/{customerId}/transactions")
public class CreditTransactionController {

    private final CustomerCreditService customerCreditService;

    public CreditTransactionController(CustomerCreditService customerCreditService) {
        this.customerCreditService = customerCreditService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> applyTransaction(@PathVariable UUID customerId,
                                                             @Valid @RequestBody CreateTransactionRequest request) {
        var customer = customerCreditService.applyTransaction(
                customerId,
                request.getEmployeeId(),
                request.getType(),
                request.getAmount(),
                request.getReason()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(new CustomerResponse(customer));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getTransactionHistory(@PathVariable UUID customerId,
                                                                           Pageable pageable) {
        Page<TransactionResponse> history = customerCreditService.getTransactionHistory(customerId, pageable)
                .map(TransactionResponse::new);
        return ResponseEntity.ok(history);
    }
}
