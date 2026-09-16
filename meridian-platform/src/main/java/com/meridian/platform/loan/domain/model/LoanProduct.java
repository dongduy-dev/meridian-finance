package com.meridian.platform.loan.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

public record LoanProduct(
        UUID id,
        ProductCode productCode,
        ProductType productType,
        String name,
        String description,
        boolean active,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {

    private static final int MAX_INTEGER_DIGITS = 17;
    private static final int MAX_FRACTION_DIGITS = 2;

    public LoanProduct updateLimits(BigDecimal newMinAmount, BigDecimal newMaxAmount) {
        validateLimit(newMinAmount);
        validateLimit(newMaxAmount);
        if (newMaxAmount.compareTo(newMinAmount) < 0) {
            throw invalidLimits();
        }
        if (minAmount.compareTo(newMinAmount) == 0 && maxAmount.compareTo(newMaxAmount) == 0) {
            return this;
        }
        return new LoanProduct(
                id, productCode, productType, name, description, active, newMinAmount, newMaxAmount
        );
    }

    public LoanProduct changeActivation(boolean targetActive) {
        if (active == targetActive) {
            return this;
        }
        return new LoanProduct(
                id, productCode, productType, name, description, targetActive, minAmount, maxAmount
        );
    }

    private static void validateLimit(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        int fractionDigits = Math.max(amount.scale(), 0);
        int integerDigits = Math.max(amount.precision() - amount.scale(), 0);
        if (amount.signum() < 0
                || fractionDigits > MAX_FRACTION_DIGITS
                || integerDigits > MAX_INTEGER_DIGITS) {
            throw invalidLimits();
        }
    }

    private static BusinessRuleViolationException invalidLimits() {
        return new BusinessRuleViolationException(
                "INVALID_PRODUCT_LIMITS",
                "Loan product limits are invalid."
        );
    }
}
