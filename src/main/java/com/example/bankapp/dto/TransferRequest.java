package com.example.bankapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class TransferRequest {

    @NotBlank(message = "Sender account is required.")
    private String fromAccount;

    @NotBlank(message = "Receiver account is required.")
    private String toAccount;

    @NotNull(message = "Amount is required.")
    @DecimalMin(value = "0.01",
            message = "Amount must be greater than zero.")
    @Digits(integer = 12, fraction = 2,
            message = "Invalid monetary amount.")
    private BigDecimal amount;

    private boolean simulateFailure;

}