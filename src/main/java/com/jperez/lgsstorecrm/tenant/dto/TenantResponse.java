package com.jperez.lgsstorecrm.tenant.dto;

import com.jperez.lgsstorecrm.tenant.Tenant;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class TenantResponse {

    private final UUID id;
    private final String name;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public TenantResponse(Tenant tenant) {
        this.id = tenant.getId();
        this.name = tenant.getName();
        this.createdAt = tenant.getCreatedAt();
        this.updatedAt = tenant.getUpdatedAt();
    }
}
