package com.jperez.lgsstorecrm.transaction;

import com.jperez.lgsstorecrm.common.exception.CustomerNotFoundException;
import com.jperez.lgsstorecrm.common.exception.EmployeeNotFoundException;
import com.jperez.lgsstorecrm.common.exception.InsufficientCreditException;
import com.jperez.lgsstorecrm.customer.Customer;
import com.jperez.lgsstorecrm.customer.CustomerRepository;
import com.jperez.lgsstorecrm.employee.Employee;
import com.jperez.lgsstorecrm.employee.EmployeeRepository;
import com.jperez.lgsstorecrm.employee.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerCreditServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CreditTransactionRepository creditTransactionRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    private CustomerCreditService customerCreditService;

    private UUID customerId;
    private UUID employeeId;
    private Customer customer;
    private Employee employee;

    @BeforeEach
    void setUp() {
        customerCreditService = new CustomerCreditService(
                customerRepository, creditTransactionRepository, employeeRepository
        );

        customerId = UUID.randomUUID();
        employeeId = UUID.randomUUID();

        customer = new Customer("Jane", "Doe", "555-1234", "123 Main St");
        customer.setStoreCredit(new BigDecimal("50.00"));

        employee = new Employee("John", "Smith", Role.EMPLOYEE);
    }

    @Test
    void creditTransaction_increasesBalance() {
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        Customer result = customerCreditService.applyTransaction(
                customerId, employeeId, TransactionType.CREDIT, new BigDecimal("25.00"), "Refund"
        );

        assertThat(result.getStoreCredit()).isEqualByComparingTo("75.00");
        verify(customerRepository).save(customer);
    }

    @Test
    void debitTransaction_decreasesBalance() {
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        Customer result = customerCreditService.applyTransaction(
                customerId, employeeId, TransactionType.DEBIT, new BigDecimal("20.00"), "Purchase"
        );

        assertThat(result.getStoreCredit()).isEqualByComparingTo("30.00");
    }

    @Test
    void debitTransaction_exceedingBalance_throwsInsufficientCreditException() {
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> customerCreditService.applyTransaction(
                customerId, employeeId, TransactionType.DEBIT, new BigDecimal("100.00"), "Purchase"
        )).isInstanceOf(InsufficientCreditException.class);

        // Balance must remain untouched, and nothing should be persisted
        verify(customerRepository, never()).save(any());
        verify(creditTransactionRepository, never()).save(any());
    }

    @Test
    void debitTransaction_exactBalance_succeedsAndResultsInZero() {
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        Customer result = customerCreditService.applyTransaction(
                customerId, employeeId, TransactionType.DEBIT, new BigDecimal("50.00"), "Full redemption"
        );

        assertThat(result.getStoreCredit()).isEqualByComparingTo("0.00");
    }

    @Test
    void applyTransaction_nonexistentCustomer_throwsCustomerNotFoundException() {
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerCreditService.applyTransaction(
                customerId, employeeId, TransactionType.CREDIT, new BigDecimal("10.00"), "Test"
        )).isInstanceOf(CustomerNotFoundException.class);

        verifyNoInteractions(employeeRepository);
        verify(creditTransactionRepository, never()).save(any());
    }

    @Test
    void applyTransaction_nonexistentEmployee_throwsEmployeeNotFoundException() {
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerCreditService.applyTransaction(
                customerId, employeeId, TransactionType.CREDIT, new BigDecimal("10.00"), "Test"
        )).isInstanceOf(EmployeeNotFoundException.class);

        verify(customerRepository, never()).save(any());
        verify(creditTransactionRepository, never()).save(any());
    }

    @Test
    void applyTransaction_savesTransactionWithCorrectResultingBalance() {
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        customerCreditService.applyTransaction(
                customerId, employeeId, TransactionType.CREDIT, new BigDecimal("15.00"), "Store promo"
        );

        ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).save(captor.capture());

        CreditTransaction saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(saved.getAmount()).isEqualByComparingTo("15.00");
        assertThat(saved.getResultingBalance()).isEqualByComparingTo("65.00");
        assertThat(saved.getReason()).isEqualTo("Store promo");
        assertThat(saved.getCustomer()).isEqualTo(customer);
        assertThat(saved.getEmployee()).isEqualTo(employee);
    }
}
