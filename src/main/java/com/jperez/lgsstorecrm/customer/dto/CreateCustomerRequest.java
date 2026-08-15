package com.jperez.lgsstorecrm.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCustomerRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @Pattern(regexp = "^\\+?[0-9\\-() ]{7,20}$", message = "Phone number format is invalid")
    private String phoneNumber;

    @Size(max = 255)
    private String address;
}
