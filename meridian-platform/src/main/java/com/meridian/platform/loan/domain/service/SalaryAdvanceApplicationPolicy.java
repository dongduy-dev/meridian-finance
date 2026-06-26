package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.VerifiedPartnerEmployeeLinkSnapshot;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

public class SalaryAdvanceApplicationPolicy {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
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

    public void validateEmployeeConfiguredLimit(
            VerifiedPartnerEmployeeLinkSnapshot partnerSnapshot,
            BigDecimal requestedAmount
    ) {
        Objects.requireNonNull(partnerSnapshot, "partnerSnapshot must not be null");
        Objects.requireNonNull(requestedAmount, "requestedAmount must not be null");

        if (partnerSnapshot.employeeSalaryAdvanceLimit().compareTo(requestedAmount) < 0) {
            throw new BusinessRuleViolationException(
                    "INSUFFICIENT_AVAILABLE_LIMIT",
                    "Requested amount exceeds employee configured Salary Advance limit."
            );
        }
    }
}
