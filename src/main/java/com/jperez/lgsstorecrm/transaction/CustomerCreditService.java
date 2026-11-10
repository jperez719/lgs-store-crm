package com.jperez.lgsstorecrm.transaction;

import com.jperez.lgsstorecrm.common.config.RabbitMQConfig;
import com.jperez.lgsstorecrm.common.exception.CustomerNotFoundException;
import com.jperez.lgsstorecrm.common.exception.EmployeeNotFoundException;
import com.jperez.lgsstorecrm.common.exception.IdempotencyKeyReuseException;
import com.jperez.lgsstorecrm.common.exception.InsufficientCreditException;
import com.jperez.lgsstorecrm.customer.Customer;
import com.jperez.lgsstorecrm.customer.CustomerRepository;
import com.jperez.lgsstorecrm.employee.Employee;
import com.jperez.lgsstorecrm.employee.EmployeeRepository;
import com.jperez.lgsstorecrm.transaction.event.CreditTransactionAppliedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerCreditService {

    private final CustomerRepository customerRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final EmployeeRepository employeeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CustomerCreditService(CustomerRepository customerRepository,
                                 CreditTransactionRepository creditTransactionRepository,
                                 EmployeeRepository employeeRepository, ApplicationEventPublisher eventPublisher) {
        this.customerRepository = customerRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.employeeRepository = employeeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Customer applyTransaction(UUID tenantId, UUID customerId, UUID employeeId,
                                     TransactionType type, BigDecimal amount, String reason) {
        return applyTransaction(tenantId, customerId, employeeId, type, amount, reason, null);
    }

    @Transactional
    public Customer applyTransaction(UUID tenantId, UUID customerId, UUID employeeId,
                                     TransactionType type, BigDecimal amount, String reason,
                                     String idempotencyKey) {

        Customer customer = customerRepository.findByIdAndTenantIdForUpdate(customerId, tenantId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        // Replay check: must happen AFTER the lock, so a concurrent retry waits here
        // and then sees the first request's committed row.
        if (idempotencyKey != null) {
            Optional<CreditTransaction> existing =
                    creditTransactionRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
            if (existing.isPresent()) {
                assertSameRequest(existing.get(), customerId, employeeId, type, amount, reason);
                return customer;   // replay: no new row, no event, no balance change
            }
        }

        Employee employee = employeeRepository.findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        BigDecimal currentBalance = customer.getStoreCredit();
        BigDecimal newBalance = type == TransactionType.CREDIT
                ? currentBalance.add(amount)
                : currentBalance.subtract(amount);

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientCreditException(customerId, amount, currentBalance);
        }

        customer.setStoreCredit(newBalance);
        customerRepository.save(customer);

        CreditTransaction transaction = new CreditTransaction(
                customer.getTenant(), customer, employee, type, amount, reason, newBalance, idempotencyKey);

        creditTransactionRepository.save(transaction);

        eventPublisher.publishEvent(new CreditTransactionAppliedEvent(
                tenantId, customerId, employeeId, transaction.getId(),
                type, amount, newBalance, LocalDateTime.now()
        ));

        return customer;
    }

    @Transactional(readOnly = true)
    public Page<CreditTransaction> getTransactionHistory(UUID tenantId, UUID customerId, Pageable pageable) {
        if (customerRepository.findByIdAndTenantId(customerId, tenantId).isEmpty()) {
            throw new CustomerNotFoundException(customerId);
        }
        return creditTransactionRepository.findByCustomerIdAndTenantIdOrderByCreatedAtDesc(
                customerId, tenantId, pageable);
    }

    private void assertSameRequest(CreditTransaction existing, UUID customerId, UUID employeeId,
                                   TransactionType type, BigDecimal amount, String reason) {
        boolean sameEmployee = existing.getEmployee() != null
                && existing.getEmployee().getId().equals(employeeId);
        boolean same = existing.getCustomer().getId().equals(customerId)
                && sameEmployee
                && existing.getType() == type
                && existing.getAmount().compareTo(amount) == 0
                && Objects.equals(existing.getReason(), reason);
        if (!same) {
            throw new IdempotencyKeyReuseException(existing.getIdempotencyKey());
        }
    }
}