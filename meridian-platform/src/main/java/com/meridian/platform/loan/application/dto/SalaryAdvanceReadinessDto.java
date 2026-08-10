package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SalaryAdvanceReadinessDto(
        String productCode,
        UUID customerPartnerEmployeeLinkId,
        String employeeVerificationStatus,
        String partnerEligibilityStatus,
        String limitStatus,
        BigDecimal totalAmount,
        BigDecimal usedAmount,
        BigDecimal reservedAmount,
        BigDecimal availableAmount,
        LocalDateTime lastRefreshAt,
        boolean applicationAllowed,
        List<String> blockerCodes
) {

    public SalaryAdvanceReadinessDto {
        productCode = requireText(productCode, "productCode");
        employeeVerificationStatus = requireText(
                employeeVerificationStatus,
                "employeeVerificationStatus"
        );
        partnerEligibilityStatus = requireText(partnerEligibilityStatus, "partnerEligibilityStatus");
        limitStatus = requireText(limitStatus, "limitStatus");
        Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        Objects.requireNonNull(usedAmount, "usedAmount must not be null");
        Objects.requireNonNull(reservedAmount, "reservedAmount must not be null");
        Objects.requireNonNull(availableAmount, "availableAmount must not be null");
        blockerCodes = List.copyOf(Objects.requireNonNull(blockerCodes, "blockerCodes must not be null"));
        if (applicationAllowed != blockerCodes.isEmpty()) {
            throw new IllegalArgumentException("applicationAllowed must agree with blockerCodes.");
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
