package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.util.Objects;

public record ApprovedOfferFinancialTerms(
        BigDecimal approvedPrincipal,
        int approvedTermMonths,
        InterestCalculationMethod interestCalculationMethod,
        BigDecimal flatMonthlyInterestRate,
        BigDecimal totalInterest,
        BigDecimal feeAmount,
        BigDecimal totalRepaymentAmount,
        RepaymentMethod repaymentMethod
) {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    public ApprovedOfferFinancialTerms {
        Objects.requireNonNull(approvedPrincipal, "approvedPrincipal must not be null");
        Objects.requireNonNull(interestCalculationMethod, "interestCalculationMethod must not be null");
        Objects.requireNonNull(flatMonthlyInterestRate, "flatMonthlyInterestRate must not be null");
        Objects.requireNonNull(totalInterest, "totalInterest must not be null");
        Objects.requireNonNull(feeAmount, "feeAmount must not be null");
        Objects.requireNonNull(totalRepaymentAmount, "totalRepaymentAmount must not be null");
        Objects.requireNonNull(repaymentMethod, "repaymentMethod must not be null");

        requirePositiveWholeVnd(approvedPrincipal, "approvedPrincipal");
        if (approvedTermMonths <= 0) {
            throw invalidPolicy("approvedTermMonths must be positive.");
        }
        requireNonNegative(flatMonthlyInterestRate, "flatMonthlyInterestRate");
        requireNonNegativeWholeVnd(totalInterest, "totalInterest");
        requireNonNegativeWholeVnd(feeAmount, "feeAmount");
        requireNonNegativeWholeVnd(totalRepaymentAmount, "totalRepaymentAmount");

        if (totalRepaymentAmount.compareTo(approvedPrincipal.add(totalInterest).add(feeAmount)) != 0) {
            throw invalidPolicy("totalRepaymentAmount must equal principal, interest, and fee.");
        }
    }

    private static void requirePositiveWholeVnd(BigDecimal value, String fieldName) {
        requireNonNegativeWholeVnd(value, fieldName);
        if (value.compareTo(ZERO) <= 0) {
            throw invalidPolicy(fieldName + " must be positive.");
        }
    }

    private static void requireNonNegativeWholeVnd(BigDecimal value, String fieldName) {
        requireNonNegative(value, fieldName);
        if (value.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw invalidPolicy(fieldName + " must be a whole VND amount.");
        }
    }

    private static void requireNonNegative(BigDecimal value, String fieldName) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw invalidPolicy(fieldName + " must not be negative.");
        }
    }

    private static BusinessRuleViolationException invalidPolicy(String message) {
        return new BusinessRuleViolationException("PRODUCT_POLICY_INVALID", message);
    }
}
