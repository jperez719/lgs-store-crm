package com.jperez.lgsstorecrm.customer;

import com.jperez.lgsstorecrm.common.exception.CustomerNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer createCustomer(String firstName, String lastName, String phoneNumber, String address) {
        Customer customer = new Customer(firstName, lastName, phoneNumber, address);
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public Customer getCustomer(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    @Transactional(readOnly = true)
    public Page<Customer> listCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable);
    }

    @Transactional
    public Customer updateContactInfo(UUID customerId, String firstName, String lastName,
                                      String phoneNumber, String address) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setPhoneNumber(phoneNumber);
        customer.setAddress(address);

        return customerRepository.save(customer);
    }
}
