package com.jperez.lgsstorecrm.customer.dto;

import com.jperez.lgsstorecrm.customer.Customer;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class CustomerResponse {

    private final UUID id;
    private final String firstName;
    private final String lastName;
    private final String phoneNumber;
    private final String address;
    private final BigDecimal storeCredit;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public CustomerResponse(Customer customer) {
        this.id = customer.getId();
        this.firstName = customer.getFirstName();
        this.lastName = customer.getLastName();
        this.phoneNumber = customer.getPhoneNumber();
        this.address = customer.getAddress();
        this.storeCredit = customer.getStoreCredit();
        this.createdAt = customer.getCreatedAt();
        this.updatedAt = customer.getUpdatedAt();
    }
}
