package com.jperez.lgsstorecrm.transaction;

import com.jperez.lgsstorecrm.customer.Customer;
import com.jperez.lgsstorecrm.customer.CustomerRepository;
import com.jperez.lgsstorecrm.employee.Employee;
import com.jperez.lgsstorecrm.employee.EmployeeRepository;
import com.jperez.lgsstorecrm.employee.Role;
import com.jperez.lgsstorecrm.tenant.Tenant;
import com.jperez.lgsstorecrm.tenant.TenantRepository;
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
import java.util.*;
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

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    void concurrentCreditTransactions_onSameCustomer_bothApplyCorrectly() throws InterruptedException {
        Tenant tenant = tenantRepository.save(new Tenant("Test Store"));

        Customer customer = new Customer("Jane", "Doe", "555-1234", "123 Main St");
        customer.setTenant(tenant);
        customer = customerRepository.save(customer);

        Employee employee = new Employee("John", "Smith", Role.EMPLOYEE);
        employee.setTenant(tenant);
        employee = employeeRepository.save(employee);

        UUID tenantId = tenant.getId();
        UUID customerId = customer.getId();
        UUID employeeId = employee.getId();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        Runnable creditTask = () -> {
            try {
                readyLatch.countDown();
                startLatch.await();
                customerCreditService.applyTransaction(
                        tenantId, customerId, employeeId, TransactionType.CREDIT,
                        new BigDecimal("50.00"), "Concurrent credit"
                );
            } catch (Throwable t) {
                failures.add(t);
            } finally {
                doneLatch.countDown();
            }
        };

        executor.submit(creditTask);
        executor.submit(creditTask);

        readyLatch.await();
        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(failures).isEmpty(); // <-- this will now surface the real exception if one occurred

        Customer updated = customerRepository.findByIdAndTenantId(customerId, tenantId).orElseThrow();
        assertThat(updated.getStoreCredit()).isEqualByComparingTo("100.00");
    }

    @Test
    void customerFromOneTenant_isNotAccessibleUnderAnotherTenantId() {
        Tenant tenantA = tenantRepository.save(new Tenant("Store A"));
        Tenant tenantB = tenantRepository.save(new Tenant("Store B"));

        Customer customerInTenantA = new Customer("Alice", "Anderson", "555-1111", "1 A St");
        customerInTenantA.setTenant(tenantA);
        customerInTenantA = customerRepository.save(customerInTenantA);

        // The critical assertion: looking up Tenant A's customer using Tenant B's id
        // must behave exactly as if the customer does not exist.
        Optional<Customer> result = customerRepository.findByIdAndTenantId(
                customerInTenantA.getId(), tenantB.getId()
        );

        assertThat(result).isEmpty();

        // And confirm it genuinely does exist, correctly, under its real tenant.
        Optional<Customer> correctLookup = customerRepository.findByIdAndTenantId(
                customerInTenantA.getId(), tenantA.getId()
        );
        assertThat(correctLookup).isPresent();
    }
}