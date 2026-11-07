package com.example.bankapp.config;

import com.example.bankapp.model.Customer;

import com.example.bankapp.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final CustomerRepository customerRepository;

    @Override
    public void run(String... args) {

        // Insert sample data only when the table is empty
        if (customerRepository.countCustomersNative() == 0) {

            createCustomer(
                    "100001",
                    "Kamal",
                    "kamal@example.com",
                    "50000.00"
            );

            createCustomer(
                    "100002",
                    "Nimali",
                    "nimali@example.com",
                    "25000.00"
            );

            createCustomer(
                    "100003",
                    "Saman",
                    "saman@example.com",
                    "10000.00"
            );
        }

    }

    private void createCustomer(
            String accountNumber,
            String fullName,
            String email,
            String balance) {

        Customer customer = new Customer();

        customer.setAccountNumber(accountNumber);
        customer.setFullName(fullName);
        customer.setEmail(email);

        customer.setBalance(new BigDecimal(balance));

        customerRepository.save(customer);
    }

}