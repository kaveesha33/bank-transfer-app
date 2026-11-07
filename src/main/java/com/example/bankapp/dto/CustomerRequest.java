package com.example.bankapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class CustomerRequest {

    @NotBlank(message = "Full name is required.")
    private String fullName;

    @NotBlank(message = "Account number is required.")
    private String accountNumber;

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    private String email;

    @NotNull(message = "Initial balance is required.")
    @DecimalMin(value = "0.00",
            message = "Balance cannot be negative.")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal initialBalance;

}