package com.jperez.lgsstorecrm.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTenantRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 150)
    private String name;
}
