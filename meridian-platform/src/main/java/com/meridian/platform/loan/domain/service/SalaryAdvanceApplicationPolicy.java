package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.VerifiedPartnerEmployeeLinkSnapshot;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Set;

public class SalaryAdvanceApplicationPolicy {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    // Documented Salary Advance seed policy: 40% of monthly salary.
    private static final BigDecimal SALARY_BASED_PERCENTAGE_CAP_RATE = new BigDecimal("0.40");
    private static final int MONEY_SCALE = 2;
    private static final Set<Integer> ALLOWED_TERMS_MONTHS = Set.of(1, 2, 3);

    public void validateProduct(LoanProduct loanProduct) {
        Objects.requireNonNull(loanProduct, "loanProduct must not be null");

        if (loanProduct.productCode() != ProductCode.SALARY_ADVANCE
                || loanProduct.productType() != ProductType.SALARY_BASED) {
            throw new BusinessRuleViolationException(
                    "PRODUCT_POLICY_INVALID",
                    "Loan product is not configured as Salary Advance."
            );
        }

        if (!loanProduct.active()) {
            throw new BusinessRuleViolationException(
                    "PRODUCT_INACTIVE",
                    "Salary Advance product is inactive."
            );
        }
    }

    public void validateRequestedAmount(LoanProduct loanProduct, BigDecimal requestedAmount) {
        Objects.requireNonNull(loanProduct, "loanProduct must not be null");
        Objects.requireNonNull(requestedAmount, "requestedAmount must not be null");

        if (requestedAmount.compareTo(ZERO) <= 0) {
            throw new BusinessRuleViolationException(
                    "INVALID_PRODUCT_AMOUNT",
                    "Requested amount must be positive."
            );
        }

        if (requestedAmount.compareTo(loanProduct.minAmount()) < 0
                || requestedAmount.compareTo(loanProduct.maxAmount()) > 0) {
            throw new BusinessRuleViolationException(
                    "INVALID_PRODUCT_AMOUNT",
                    "Requested amount is outside Salary Advance product limits."
            );
        }
    }

    public void validateRequestedTerm(int requestedTermMonths) {
        if (!ALLOWED_TERMS_MONTHS.contains(requestedTermMonths)) {
            throw new BusinessRuleViolationException(
                    "INVALID_PRODUCT_TERM",
                    "Requested term is not allowed for Salary Advance."
            );
        }
    }

    public BigDecimal calculateEffectiveTotalLimit(
            LoanProduct loanProduct,
            VerifiedPartnerEmployeeLinkSnapshot partnerSnapshot
    ) {
        Objects.requireNonNull(loanProduct, "loanProduct must not be null");
        Objects.requireNonNull(partnerSnapshot, "partnerSnapshot must not be null");

        requireNonNegative(loanProduct.maxAmount(), "productMaximumAmount");
        requireNonNegative(partnerSnapshot.partnerCompanySalaryAdvanceLimit(), "partnerCompanySalaryAdvanceLimit");
        requireNonNegative(partnerSnapshot.employeeSalaryAdvanceLimit(), "employeeSalaryAdvanceLimit");
        requireNonNegative(partnerSnapshot.employeeSalaryAmount(), "employeeSalaryAmount");

        return min(
                loanProduct.maxAmount(),
                partnerSnapshot.partnerCompanySalaryAdvanceLimit(),
                partnerSnapshot.employeeSalaryAdvanceLimit(),
                calculateSalaryBasedLimit(partnerSnapshot.employeeSalaryAmount())
        );
    }

    private BigDecimal calculateSalaryBasedLimit(BigDecimal employeeSalaryAmount) {
        return employeeSalaryAmount
                .multiply(SALARY_BASED_PERCENTAGE_CAP_RATE)
                .setScale(MONEY_SCALE, RoundingMode.DOWN);
    }

    private BigDecimal min(BigDecimal first, BigDecimal... others) {
        BigDecimal minimum = first;
        for (BigDecimal value : others) {
            if (value.compareTo(minimum) < 0) {
                minimum = value;
            }
        }
        return minimum;
    }

    private void requireNonNegative(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.compareTo(ZERO) < 0) {
            throw new BusinessRuleViolationException(
                    "PRODUCT_POLICY_INVALID",
                    fieldName + " must not be negative."
            );
        }
    }
}
