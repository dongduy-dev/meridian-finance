package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ProvisionalRepaymentItem(
        UUID id,
        int installmentNumber,
        BigDecimal principalDue,
        BigDecimal interestDue,
        BigDecimal feeDue,
        BigDecimal totalDue
) {

    public ProvisionalRepaymentItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(principalDue, "principalDue must not be null");
        Objects.requireNonNull(interestDue, "interestDue must not be null");
        Objects.requireNonNull(feeDue, "feeDue must not be null");
        Objects.requireNonNull(totalDue, "totalDue must not be null");

        if (installmentNumber <= 0) {
            throw invalidPolicy("installmentNumber must be positive.");
        }
        requireNonNegativeWholeVnd(principalDue, "principalDue");
        requireNonNegativeWholeVnd(interestDue, "interestDue");
        requireNonNegativeWholeVnd(feeDue, "feeDue");
        requireNonNegativeWholeVnd(totalDue, "totalDue");
        if (totalDue.compareTo(principalDue.add(interestDue).add(feeDue)) != 0) {
            throw invalidPolicy("totalDue must equal principalDue, interestDue, and feeDue.");
        }
    }

    public static ProvisionalRepaymentItem of(
            UUID id,
            int installmentNumber,
            BigDecimal principalDue,
            BigDecimal interestDue,
            BigDecimal feeDue
    ) {
        return new ProvisionalRepaymentItem(
                id,
                installmentNumber,
                principalDue,
                interestDue,
                feeDue,
                principalDue.add(interestDue).add(feeDue)
        );
    }

    private static void requireNonNegativeWholeVnd(BigDecimal value, String fieldName) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw invalidPolicy(fieldName + " must not be negative.");
        }
        if (value.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw invalidPolicy(fieldName + " must be a whole VND amount.");
        }
    }

    private static BusinessRuleViolationException invalidPolicy(String message) {
        return new BusinessRuleViolationException("PRODUCT_POLICY_INVALID", message);
    }
}
