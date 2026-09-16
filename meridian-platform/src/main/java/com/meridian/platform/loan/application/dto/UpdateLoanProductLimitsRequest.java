package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateLoanProductLimitsRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal minAmount,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal maxAmount
) {
}
