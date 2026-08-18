package com.jperez.lgsstorecrm.customer;

import com.jperez.lgsstorecrm.customer.dto.CreateCustomerRequest;
import com.jperez.lgsstorecrm.customer.dto.CustomerResponse;
import com.jperez.lgsstorecrm.customer.dto.UpdateCustomerRequest;
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
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        Customer customer = customerService.createCustomer(
                request.getFirstName(),
                request.getLastName(),
                request.getPhoneNumber(),
                request.getAddress()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(new CustomerResponse(customer));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable UUID id) {
        Customer customer = customerService.getCustomer(id);
        return ResponseEntity.ok(new CustomerResponse(customer));
    }

    @GetMapping
    public ResponseEntity<Page<CustomerResponse>> listCustomers(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<CustomerResponse> customers = customerService.listCustomers(pageable)
                .map(CustomerResponse::new);
        return ResponseEntity.ok(customers);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomer(@PathVariable UUID id,
                                                           @Valid @RequestBody UpdateCustomerRequest request) {
        Customer customer = customerService.updateContactInfo(
                id,
                request.getFirstName(),
                request.getLastName(),
                request.getPhoneNumber(),
                request.getAddress()
        );
        return ResponseEntity.ok(new CustomerResponse(customer));
    }
}