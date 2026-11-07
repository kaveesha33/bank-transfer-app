package com.example.bankapp.service;

import com.example.bankapp.dto.CustomerRequest;
import com.example.bankapp.dto.TransferRequest;
import org.springframework.transaction.annotation.Propagation;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.InsufficientBalanceException;

import com.example.bankapp.model.Customer;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;

import com.example.bankapp.repository.CustomerRepository;
import com.example.bankapp.repository.BankTransactionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankService {

    private final CustomerRepository customerRepository;

    private final BankTransactionRepository transactionRepository;

    // Get customers, optionally searching by name
    public List<Customer> getCustomers(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return customerRepository.findAllOrdered();
        }

        return customerRepository.searchByName(keyword.trim());
    }

    // Get all latest transactions
    public List<BankTransaction> getLatestTransactions() {

        return transactionRepository.findAllLatest();
    }

    // Get transaction history for an account
    public List<BankTransaction> getHistory(String accountNumber) {

        return transactionRepository.findHistory(accountNumber);
    }

    // Get customer by account number
    public Customer getCustomer(String accountNumber) {

        return customerRepository
                .getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    // Calculate total balance
    public BigDecimal getTotalBalance() {

        BigDecimal total = customerRepository.getTotalBalance();

        return total == null ? BigDecimal.ZERO : total;
    }

    // Count successful or failed transactions
    public long countByStatus(TransactionStatus status) {

        return transactionRepository.countByStatus(status);
    }

    // Create a new customer
    @Transactional
    public Customer createCustomer(CustomerRequest request) {

        if (customerRepository.countAccounts(
                request.getAccountNumber()) > 0) {

            throw new BankException(
                    "Account number already exists.");
        }

        if (customerRepository.countEmails(
                request.getEmail()) > 0) {

            throw new BankException(
                    "Email already exists.");
        }

        Customer customer = new Customer();

        customer.setAccountNumber(request.getAccountNumber());
        customer.setFullName(request.getFullName());
        customer.setEmail(request.getEmail());
        customer.setBalance(request.getInitialBalance());

        return customerRepository.save(customer);
    }

    // THE MOST IMPORTANT METHOD
    // Transfer money between two accounts

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction transfer(TransferRequest request) throws Exception {

        String fromAccount = request.getFromAccount();
        String toAccount = request.getToAccount();

        BigDecimal amount = request.getAmount();

        // 1. Validate that sender and receiver are different
        if (fromAccount.equals(toAccount)) {

            throw new BankException(
                    "Sender and receiver must be different accounts.");
        }

        // 2. Check that both accounts exist
        customerRepository
                .getByAccountNumber(fromAccount)
                .orElseThrow(() -> new AccountNotFoundException(fromAccount));

        customerRepository
                .getByAccountNumber(toAccount)
                .orElseThrow(() -> new AccountNotFoundException(toAccount));

        // 3. Debit the sender
        int debitResult = customerRepository.debit(
                fromAccount, amount);

        if (debitResult == 0) {

            throw new InsufficientBalanceException(fromAccount);
        }

        // 4. Simulate a system failure
        if (request.isSimulateFailure()) {

            throw new BankException(
                    "All changes were rolled back due to a simulated failure.");
        }

        // 5. Credit the receiver
        int creditResult = customerRepository.credit(
                toAccount, amount);

        if (creditResult == 0) {

            throw new AccountNotFoundException(toAccount);
        }

        // 6. Save successful transaction
        BankTransaction transaction = new BankTransaction();

        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setAmount(amount);

        transaction.setStatus(TransactionStatus.SUCCESS);

        transaction.setDescription("Transfer completed successfully.");

        return transactionRepository.save(transaction);
    }

}