package com.meridian.platform.loan.domain.model.salaryadvance;

import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record SalaryAdvanceOfferPolicy(
        UUID id,
        InterestCalculationMethod interestCalculationMethod,
        BigDecimal flatMonthlyInterestRate,
        BigDecimal feeAmount,
        RepaymentMethod repaymentMethod,
        int offerValidityDays,
        Set<Integer> allowedTermsMonths
) {

    private static final Set<Integer> REQUIRED_TERMS = Set.of(1, 2, 3);

    public SalaryAdvanceOfferPolicy {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(interestCalculationMethod, "interestCalculationMethod must not be null");
        Objects.requireNonNull(flatMonthlyInterestRate, "flatMonthlyInterestRate must not be null");
        Objects.requireNonNull(feeAmount, "feeAmount must not be null");
        Objects.requireNonNull(repaymentMethod, "repaymentMethod must not be null");
        allowedTermsMonths = Set.copyOf(Objects.requireNonNull(allowedTermsMonths, "allowedTermsMonths must not be null"));

        if (interestCalculationMethod != InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL) {
            throw invalidPolicy("Salary Advance requires flat original-principal interest.");
        }
        if (flatMonthlyInterestRate.compareTo(BigDecimal.ZERO) < 0) {
            throw invalidPolicy("Salary Advance interest rate must not be negative.");
        }
        if (feeAmount.compareTo(BigDecimal.ZERO) != 0) {
            throw invalidPolicy("Salary Advance fee must be zero.");
        }
        if (repaymentMethod != RepaymentMethod.ON_SALARY_DATE) {
            throw invalidPolicy("Salary Advance repayment method must be ON_SALARY_DATE.");
        }
        if (offerValidityDays <= 0) {
            throw invalidPolicy("Salary Advance offer validity must be positive.");
        }
        if (!allowedTermsMonths.containsAll(REQUIRED_TERMS)) {
            throw invalidPolicy("Salary Advance policy must allow 1, 2, and 3 month terms.");
        }
    }

    public void validateApprovedTerm(int approvedTermMonths) {
        if (!allowedTermsMonths.contains(approvedTermMonths)) {
            throw new BusinessRuleViolationException(
                    "INVALID_PRODUCT_TERM",
                    "Approved Salary Advance term is not allowed by the active policy."
            );
        }
    }

    private static BusinessRuleViolationException invalidPolicy(String message) {
        return new BusinessRuleViolationException("PRODUCT_POLICY_INVALID", message);
    }
}
