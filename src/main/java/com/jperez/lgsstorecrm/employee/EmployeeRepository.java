package com.jperez.lgsstorecrm.employee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Employee> findByTenantId(UUID tenantId, Pageable pageable);
}
