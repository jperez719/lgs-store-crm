package com.jperez.lgsstorecrm.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantRepository extends JpaRepository<com.jperez.lgsstorecrm.tenant.Tenant, UUID> {
}
