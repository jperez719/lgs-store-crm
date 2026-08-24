package com.jperez.lgsstorecrm.customer;

import com.jperez.lgsstorecrm.common.exception.CustomerNotFoundException;
import com.jperez.lgsstorecrm.common.exception.TenantNotFoundException;
import com.jperez.lgsstorecrm.tenant.Tenant;
import com.jperez.lgsstorecrm.tenant.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final TenantRepository tenantRepository;

    public CustomerService(CustomerRepository customerRepository, TenantRepository tenantRepository) {
        this.customerRepository = customerRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public Customer createCustomer(UUID tenantId, String firstName, String lastName,
                                   String phoneNumber, String address) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        Customer customer = new Customer(firstName, lastName, phoneNumber, address);
        customer.setTenant(tenant);
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public Customer getCustomer(UUID tenantId, UUID customerId) {
        return customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    @Transactional(readOnly = true)
    public Page<Customer> listCustomers(UUID tenantId, Pageable pageable) {
        return customerRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional
    public Customer updateContactInfo(UUID tenantId, UUID customerId, String firstName, String lastName,
                                      String phoneNumber, String address) {
        Customer customer = customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setPhoneNumber(phoneNumber);
        customer.setAddress(address);

        return customerRepository.save(customer);
    }
}
