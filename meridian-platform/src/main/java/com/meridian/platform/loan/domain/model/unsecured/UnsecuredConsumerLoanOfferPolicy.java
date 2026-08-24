package com.meridian.platform.loan.domain.model.unsecured;

import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record UnsecuredConsumerLoanOfferPolicy(
        UUID id,
        InterestCalculationMethod interestCalculationMethod,
        BigDecimal flatMonthlyInterestRate,
        BigDecimal feeAmount,
        RepaymentMethod repaymentMethod,
        int offerValidityDays,
        Set<Integer> allowedTermsMonths
) {

    private static final Set<Integer> REQUIRED_TERMS = Set.of(3, 6, 9, 12);

    public UnsecuredConsumerLoanOfferPolicy {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(interestCalculationMethod, "interestCalculationMethod must not be null");
        Objects.requireNonNull(flatMonthlyInterestRate, "flatMonthlyInterestRate must not be null");
        Objects.requireNonNull(feeAmount, "feeAmount must not be null");
        Objects.requireNonNull(repaymentMethod, "repaymentMethod must not be null");
        allowedTermsMonths = Set.copyOf(Objects.requireNonNull(
                allowedTermsMonths,
                "allowedTermsMonths must not be null"
        ));

        if (interestCalculationMethod != InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL) {
            throw invalidPolicy("Unsecured Consumer Loan requires flat original-principal interest.");
        }
        if (flatMonthlyInterestRate.compareTo(BigDecimal.ZERO) < 0) {
            throw invalidPolicy("Unsecured Consumer Loan interest rate must not be negative.");
        }
        if (feeAmount.compareTo(BigDecimal.ZERO) != 0) {
            throw invalidPolicy("Unsecured Consumer Loan fee must be zero.");
        }
        if (repaymentMethod != RepaymentMethod.MONTHLY_INSTALLMENT) {
            throw invalidPolicy("Unsecured Consumer Loan repayment method must be MONTHLY_INSTALLMENT.");
        }
        if (offerValidityDays <= 0) {
            throw invalidPolicy("Unsecured Consumer Loan offer validity must be positive.");
        }
        if (!allowedTermsMonths.equals(REQUIRED_TERMS)) {
            throw invalidPolicy("Unsecured Consumer Loan policy must allow exactly 3, 6, 9, and 12 month terms.");
        }
    }

    public void validateApprovedTerm(int approvedTermMonths) {
        if (!allowedTermsMonths.contains(approvedTermMonths)) {
            throw new BusinessRuleViolationException(
                    "INVALID_PRODUCT_TERM",
                    "Approved Unsecured Consumer Loan term is not allowed by the active policy."
            );
        }
    }

    private static BusinessRuleViolationException invalidPolicy(String message) {
        return new BusinessRuleViolationException("PRODUCT_POLICY_INVALID", message);
    }
}
