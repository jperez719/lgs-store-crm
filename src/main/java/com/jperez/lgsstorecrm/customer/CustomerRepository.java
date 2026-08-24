package com.jperez.lgsstorecrm.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    @Deprecated
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.id = :id AND c.tenant.id = :tenantId")
    Optional<Customer> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Customer> findByTenantId(UUID tenantId, Pageable pageable);
}
