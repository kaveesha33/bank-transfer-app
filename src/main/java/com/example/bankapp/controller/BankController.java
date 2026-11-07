package com.example.bankapp.controller;

import com.example.bankapp.dto.AccountAmountRequest;
import com.example.bankapp.dto.CustomerRequest;
import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;

import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

import java.math.BigDecimal;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class BankController {

        private final BankService bankService;

        private final TransactionAuditService auditService;

        // 1. Display dashboard
        @GetMapping("/")
        public String dashboard(
                        @RequestParam(required = false) String keyword,
                        Model model) {

                loadDashboard(model, keyword);

                model.addAttribute("transferRequest", new TransferRequest());

                model.addAttribute("customerRequest", new CustomerRequest());

                model.addAttribute("keyword",
                                keyword == null ? "" : keyword);

                return "bank";
        }

        // 2. Add a new customer
        @PostMapping("/customers")
        public String addCustomer(
                        @Valid @ModelAttribute("customerRequest") CustomerRequest request,

                        BindingResult result,

                        Model model,

                        RedirectAttributes redirectAttributes) {

                if (result.hasErrors()) {

                        loadDashboard(model, null);

                        model.addAttribute("transferRequest",
                                        new TransferRequest());

                        return "bank";
                }

                try {

                        bankService.createCustomer(request);

                        redirectAttributes.addFlashAttribute(
                                        "successMessage",
                                        "Customer added successfully.");

                } catch (RuntimeException ex) {

                        redirectAttributes.addFlashAttribute(
                                        "errorMessage",
                                        ex.getMessage());
                }

                return "redirect:/";
        }

        // 3. Transfer money
        @PostMapping("/transfer")
        public String transfer(
                        @Valid @ModelAttribute("transferRequest") TransferRequest request,

                        BindingResult result,

                        Model model,

                        RedirectAttributes redirectAttributes) {

                if (result.hasErrors()) {

                        loadDashboard(model, null);

                        model.addAttribute("customerRequest",
                                        new CustomerRequest());

                        return "bank";
                }

                try {

                        bankService.transfer(request);

                        redirectAttributes.addFlashAttribute(
                                        "successMessage",
                                        "Transfer completed successfully.");

                } catch (Exception ex) {

                        // The transfer transaction has already rolled back.
                        // Save the FAILED transaction separately.

                        auditService.recordFailure(
                                        request.getFromAccount(),
                                        request.getToAccount(),
                                        request.getAmount(),
                                        ex.getMessage());

                        redirectAttributes.addFlashAttribute(
                                        "errorMessage",
                                        ex.getMessage());
                }

                return "redirect:/";
        }

        @PostMapping(value = "/transfer", consumes = MediaType.APPLICATION_JSON_VALUE)
        @ResponseBody
        public ResponseEntity<String> transferJson(
                        @Valid @RequestBody TransferRequest request) {

                try {

                        bankService.transfer(request);

                        return ResponseEntity.ok(
                                        "Transfer completed successfully.");

                } catch (Exception ex) {

                        auditService.recordFailure(
                                        request.getFromAccount(),
                                        request.getToAccount(),
                                        request.getAmount(),
                                        ex.getMessage());

                        return ResponseEntity.badRequest()
                                        .body(ex.getMessage());
                }
        }

        @PostMapping(value = "/customers", consumes = MediaType.APPLICATION_JSON_VALUE)
        @ResponseBody
        public ResponseEntity<String> addCustomerJson(
                        @Valid @RequestBody CustomerRequest request) {

                try {

                        bankService.createCustomer(request);

                        return ResponseEntity.ok(
                                        "Customer added successfully.");

                } catch (RuntimeException ex) {

                        return ResponseEntity.badRequest()
                                        .body(ex.getMessage());
                }
        }

        // 4. View account transaction history
        @GetMapping("/accounts/{accountNumber}/history")
        public String history(
                        @PathVariable String accountNumber,
                        Model model) {

                model.addAttribute(
                                "customer",
                                bankService.getCustomer(accountNumber));

                model.addAttribute(
                                "transactions",
                                bankService.getHistory(accountNumber));

                model.addAttribute(
                                "totalSent",
                                bankService.getTotalSuccessfulSent(accountNumber));
                return "history";
        }

        // Helper method to load dashboard information
        private void loadDashboard(Model model, String keyword) {

                model.addAttribute(
                                "customers",
                                bankService.getCustomers(keyword));

                model.addAttribute(
                                "latestTransactions",
                                bankService.getLatestTransactions());

                model.addAttribute(
                                "totalBalance",
                                bankService.getTotalBalance());

                model.addAttribute(
                                "successCount",
                                bankService.countByStatus(TransactionStatus.SUCCESS));

                model.addAttribute(
                                "failedCount",
                                bankService.countByStatus(TransactionStatus.FAILED));
        }

        // HTML page required by the assignment
        @GetMapping("/large-transfers")
        public String largeTransfersPage(
                        @RequestParam(defaultValue = "10000") BigDecimal min,
                        Model model) {

                model.addAttribute("transactions",
                                bankService.getLargeTransfers(min));

                model.addAttribute("min", min);

                return "large-transfers";
        }

        // JSON endpoint for Postman
        @GetMapping("/api/large-transfers")
        @ResponseBody
        public List<BankTransaction> largeTransfersJson(
                        @RequestParam(defaultValue = "10000") BigDecimal min) {

                return bankService.getLargeTransfers(min);
        }

        @GetMapping("/api/accounts/{accountNumber}/total-sent")
        @ResponseBody
        public BigDecimal totalSentJson(
                        @PathVariable String accountNumber) {

                return bankService.getTotalSuccessfulSent(accountNumber);
        }

        @PostMapping(value = "/api/deposit", consumes = MediaType.APPLICATION_JSON_VALUE)
        @ResponseBody
        public ResponseEntity<String> depositJson(
                        @Valid @RequestBody AccountAmountRequest request) {

                try {
                        bankService.deposit(
                                        request.getAccountNumber(),
                                        request.getAmount());

                        return ResponseEntity.ok("Deposit completed successfully.");

                } catch (RuntimeException ex) {

                        auditService.recordFailure(
                                        "EXTERNAL",
                                        request.getAccountNumber(),
                                        request.getAmount(),
                                        ex.getMessage());

                        return ResponseEntity.badRequest().body(ex.getMessage());
                }
        }

        @PostMapping(value = "/api/withdraw", consumes = MediaType.APPLICATION_JSON_VALUE)
        @ResponseBody
        public ResponseEntity<String> withdrawJson(
                        @Valid @RequestBody AccountAmountRequest request) {

                try {
                        bankService.withdraw(
                                        request.getAccountNumber(),
                                        request.getAmount());

                        return ResponseEntity.ok("Withdrawal completed successfully.");

                } catch (RuntimeException ex) {

                        auditService.recordFailure(
                                        request.getAccountNumber(),
                                        "EXTERNAL",
                                        request.getAmount(),
                                        ex.getMessage());

                        return ResponseEntity.badRequest().body(ex.getMessage());
                }
        }

        @PostMapping("/deposit")
        public String depositForm(
                        @RequestParam String accountNumber,
                        @RequestParam BigDecimal amount,
                        RedirectAttributes redirectAttributes) {

                try {
                        bankService.deposit(accountNumber, amount);
                        redirectAttributes.addFlashAttribute(
                                        "successMessage", "Deposit completed successfully.");

                } catch (RuntimeException ex) {

                        auditService.recordFailure(
                                        "EXTERNAL", accountNumber, amount, ex.getMessage());

                        redirectAttributes.addFlashAttribute(
                                        "errorMessage", ex.getMessage());
                }

                return "redirect:/";
        }

        @PostMapping("/withdraw")
        public String withdrawForm(
                        @RequestParam String accountNumber,
                        @RequestParam BigDecimal amount,
                        RedirectAttributes redirectAttributes) {

                try {
                        bankService.withdraw(accountNumber, amount);
                        redirectAttributes.addFlashAttribute(
                                        "successMessage", "Withdrawal completed successfully.");

                } catch (RuntimeException ex) {

                        auditService.recordFailure(
                                        accountNumber, "EXTERNAL", amount, ex.getMessage());

                        redirectAttributes.addFlashAttribute(
                                        "errorMessage", ex.getMessage());
                }

                return "redirect:/";
        }
}