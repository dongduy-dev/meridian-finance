package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record SalaryAdvanceApplicationRequest(
        @NotNull UUID customerId,
        @NotNull UUID customerPartnerEmployeeLinkId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal requestedAmount,
        @NotNull @Positive Integer requestedTermMonths
) {
}
