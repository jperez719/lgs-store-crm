package com.jperez.lgsstorecrm.transaction;

import com.jperez.lgsstorecrm.customer.Customer;
import com.jperez.lgsstorecrm.customer.CustomerRepository;
import com.jperez.lgsstorecrm.employee.Employee;
import com.jperez.lgsstorecrm.employee.EmployeeRepository;
import com.jperez.lgsstorecrm.employee.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class CustomerCreditServiceConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private CustomerCreditService customerCreditService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void concurrentCreditTransactions_onSameCustomer_bothApplyCorrectly() throws InterruptedException {
        // Arrange: one customer, one employee, starting balance zero
        Customer customer = customerRepository.save(
                new Customer("Jane", "Doe", "555-1234", "123 Main St")
        );
        Employee employee = employeeRepository.save(
                new Employee("John", "Smith", Role.EMPLOYEE)
        );

        UUID customerId = customer.getId();
        UUID employeeId = employee.getId();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        Runnable creditTask = () -> {
            try {
                readyLatch.countDown();
                startLatch.await(); // both threads fire as close to simultaneously as possible
                customerCreditService.applyTransaction(
                        customerId, employeeId, TransactionType.CREDIT,
                        new BigDecimal("50.00"), "Concurrent credit"
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        };

        // Act: fire two threads at the same customer at (as close to) the same time
        executor.submit(creditTask);
        executor.submit(creditTask);

        readyLatch.await();          // wait until both threads are ready
        startLatch.countDown();      // release them simultaneously
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertThat(finished).isTrue();

        Customer updated = customerRepository.findById(customerId).orElseThrow();
        assertThat(updated.getStoreCredit()).isEqualByComparingTo("100.00");

        List<CreditTransaction> transactions =
                customerRepository.findById(customerId).orElseThrow().getTransactions();
        // (Note: lazy collection — see explanation below on why this line is commented out in practice)
    }
}
