package com.jperez.lgsstorecrm.transaction;

import com.jperez.lgsstorecrm.common.exception.CustomerNotFoundException;
import com.jperez.lgsstorecrm.common.exception.EmployeeNotFoundException;
import com.jperez.lgsstorecrm.common.exception.InsufficientCreditException;
import com.jperez.lgsstorecrm.customer.Customer;
import com.jperez.lgsstorecrm.customer.CustomerRepository;
import com.jperez.lgsstorecrm.employee.Employee;
import com.jperez.lgsstorecrm.employee.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CustomerCreditService {

    private final CustomerRepository customerRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final EmployeeRepository employeeRepository;

    public CustomerCreditService(CustomerRepository customerRepository,
                                 CreditTransactionRepository creditTransactionRepository,
                                 EmployeeRepository employeeRepository) {
        this.customerRepository = customerRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public Customer applyTransaction(UUID customerId, UUID employeeId, TransactionType type,
                                     BigDecimal amount, String reason) {

        // Row-level lock: blocks any other transaction for THIS customer until this method commits.
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        Employee employee = employeeRepository.findById(employeeId)
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
                customer, employee, type, amount, reason, newBalance
        );
        creditTransactionRepository.save(transaction);

        return customer;
    }

    @Transactional(readOnly = true)
    public Page<CreditTransaction> getTransactionHistory(UUID customerId, Pageable pageable) {
        if (!customerRepository.existsById(customerId)) {
            throw new CustomerNotFoundException(customerId);
        }
        return creditTransactionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }
}
