package com.jperez.lgsstorecrm.transaction;

import com.jperez.lgsstorecrm.customer.dto.CustomerResponse;
import com.jperez.lgsstorecrm.transaction.dto.CreateTransactionRequest;
import com.jperez.lgsstorecrm.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{tenantId}/customers/{customerId}/transactions")
public class CreditTransactionController {

    private final CustomerCreditService customerCreditService;

    public CreditTransactionController(CustomerCreditService customerCreditService) {
        this.customerCreditService = customerCreditService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> applyTransaction(@PathVariable UUID tenantId,
                                                             @PathVariable UUID customerId,
                                                             @Valid @RequestBody CreateTransactionRequest request) {
        var customer = customerCreditService.applyTransaction(
                tenantId,
                customerId,
                request.getEmployeeId(),
                request.getType(),
                request.getAmount(),
                request.getReason()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(new CustomerResponse(customer));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getTransactionHistory(
            @PathVariable UUID tenantId,
            @PathVariable UUID customerId,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<TransactionResponse> history = customerCreditService.getTransactionHistory(tenantId, customerId, pageable)
                .map(TransactionResponse::new);
        return ResponseEntity.ok(history);
    }
}
