package com.meridian.platform.loan.domain.model.collateral;

import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CollateralLoanOfferPolicy(
        UUID id,
        InterestCalculationMethod interestCalculationMethod,
        BigDecimal flatMonthlyInterestRate,
        BigDecimal feeAmount,
        RepaymentMethod repaymentMethod,
        int offerValidityDays,
        Set<Integer> allowedTermsMonths
) {

    private static final BigDecimal REQUIRED_MONTHLY_RATE = new BigDecimal("0.015000");
    private static final Set<Integer> REQUIRED_TERMS = Set.of(6, 12, 18, 24);

    public CollateralLoanOfferPolicy {
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
            throw invalidPolicy("Collateral Loan requires flat original-principal interest.");
        }
        if (flatMonthlyInterestRate.compareTo(BigDecimal.ZERO) < 0) {
            throw invalidPolicy("Collateral Loan interest rate must not be negative.");
        }
        if (flatMonthlyInterestRate.compareTo(REQUIRED_MONTHLY_RATE) != 0) {
            throw invalidPolicy("Collateral Loan monthly interest rate must be 0.015000.");
        }
        if (feeAmount.compareTo(BigDecimal.ZERO) != 0) {
            throw invalidPolicy("Collateral Loan fee must be zero.");
        }
        if (repaymentMethod != RepaymentMethod.MONTHLY_INSTALLMENT) {
            throw invalidPolicy("Collateral Loan repayment method must be MONTHLY_INSTALLMENT.");
        }
        if (offerValidityDays <= 0) {
            throw invalidPolicy("Collateral Loan offer validity must be positive.");
        }
        if (!allowedTermsMonths.equals(REQUIRED_TERMS)) {
            throw invalidPolicy("Collateral Loan policy must allow exactly 6, 12, 18, and 24 month terms.");
        }
    }

    public void validateApprovedTerm(int approvedTermMonths) {
        if (!allowedTermsMonths.contains(approvedTermMonths)) {
            throw new BusinessRuleViolationException(
                    "INVALID_PRODUCT_TERM",
                    "Approved Collateral Loan term is not allowed by the active policy."
            );
        }
    }

    private static BusinessRuleViolationException invalidPolicy(String message) {
        return new BusinessRuleViolationException("PRODUCT_POLICY_INVALID", message);
    }
}
