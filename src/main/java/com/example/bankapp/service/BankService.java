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
import java.math.RoundingMode;
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

    // 1. Validate accounts are different
    if (fromAccount.equals(toAccount)) {
        throw new BankException(
                "Sender and receiver accounts must be different"
        );
    }

    // 2. Check that all three accounts exist
    getCustomer(fromAccount);
    getCustomer(toAccount);
    getCustomer("999999");

    // 3. Calculate the 1% fee
    BigDecimal fee = amount
            .multiply(new BigDecimal("0.01"))
            .setScale(2, RoundingMode.HALF_UP);

    // 4. Calculate the total amount deducted from the sender
    BigDecimal totalDebit = amount.add(fee);

    // 5. Debit sender (transfer amount + fee)
    int debitResult = customerRepository.debit(
            fromAccount,
            totalDebit
    );

    if (debitResult != 1) {
        throw new InsufficientBalanceException(
                "Insufficient balance including transfer fee"
        );
    }

    // 6. Credit receiver (original transfer amount only)
    int receiverResult = customerRepository.credit(
            toAccount,
            amount
    );

    if (receiverResult != 1) {
        throw new BankException(
                "Could not credit receiver account"
        );
    }

    // 7. Credit the bank fee account
    if (fee.compareTo(BigDecimal.ZERO) > 0) {

        int bankResult = customerRepository.credit(
                "999999",
                fee
        );

        if (bankResult != 1) {
            throw new BankException(
                    "Could not credit bank fee account"
            );
        }
    }

    // 8. Simulate failure AFTER all three updates
    if (request.isSimulateFailure()) {
        throw new BankException(
                "Simulated system failure. All changes were rolled back."
        );
    }

    // 9. Save successful transaction record
    BankTransaction transaction = new BankTransaction();

    transaction.setFromAccount(fromAccount);
    transaction.setToAccount(toAccount);
    transaction.setAmount(amount);
    transaction.setStatus(TransactionStatus.SUCCESS);
    transaction.setDescription(
            "TRANSFER - 1% fee: " + fee.toPlainString()
    );

    return transactionRepository.save(transaction);
}

    public List<BankTransaction> getLargeTransfers(BigDecimal minAmount) {
        return transactionRepository.findTransfersAboveAmount(minAmount);
    }

    public BigDecimal getTotalSuccessfulSent(String accountNumber) {
        return transactionRepository.getTotalSuccessfulSent(
                accountNumber,
                TransactionStatus.SUCCESS);
    }

    @Transactional
    public BankTransaction deposit(String accountNumber, BigDecimal amount) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Deposit amount must be greater than zero");
        }

        // Check whether the account exists
        getCustomer(accountNumber);

        // Add money to the customer's balance
        int updatedRows = customerRepository.credit(accountNumber, amount);

        if (updatedRows != 1) {
            throw new BankException("Could not deposit money into the account");
        }

        // Create a transaction record
        BankTransaction transaction = new BankTransaction();

        transaction.setFromAccount("EXTERNAL");
        transaction.setToAccount(accountNumber);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription("DEPOSIT");

        return transactionRepository.save(transaction);
    }

    @Transactional
    public BankTransaction withdraw(String accountNumber, BigDecimal amount) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Withdrawal amount must be greater than zero");
        }

        // Check whether the account exists
        getCustomer(accountNumber);

        // Subtract money only if sufficient balance exists
        int updatedRows = customerRepository.debit(accountNumber, amount);

        if (updatedRows != 1) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        // Create a transaction record
        BankTransaction transaction = new BankTransaction();

        transaction.setFromAccount(accountNumber);
        transaction.setToAccount("EXTERNAL");
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription("WITHDRAWAL");

        return transactionRepository.save(transaction);
    }
}