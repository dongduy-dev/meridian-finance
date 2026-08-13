package com.meridian.platform.loan.application.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CollateralLoanApplicationRequest(
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal requestedAmount,
        @NotNull @Positive Integer requestedTermMonths,
        @NotNull @Valid CollateralDetailsRequest collateral
) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Unsupported Collateral Loan application field.");
    }
}
