package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record LoanApplicationStatusDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String productType,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        String status,
        LocalDateTime submittedAt
) {

    public LoanApplicationStatusDto {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        applicationNumber = requireText(applicationNumber, "applicationNumber");
        productCode = requireText(productCode, "productCode");
        productType = requireText(productType, "productType");
        Objects.requireNonNull(requestedAmount, "requestedAmount must not be null");
        if (requestedTermMonths <= 0) {
            throw new IllegalArgumentException("requestedTermMonths must be positive");
        }
        status = requireText(status, "status");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
