package com.jperez.lgsstorecrm.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, UUID> {

    Page<CreditTransaction> findByCustomerIdAndTenantIdOrderByCreatedAtDesc(
            UUID customerId, UUID tenantId, Pageable pageable);
}
