package com.meridian.platform.loan.domain.service.unsecured;

import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

public class UnsecuredConsumerLoanApplicationPolicy {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal MINIMUM_AMOUNT = new BigDecimal("2000000");
    private static final BigDecimal MAXIMUM_AMOUNT = new BigDecimal("50000000");
    private static final Set<Integer> ALLOWED_TERMS_MONTHS = Set.of(3, 6, 9, 12);

    public void validateProduct(LoanProduct loanProduct) {
        Objects.requireNonNull(loanProduct, "loanProduct must not be null");
        if (loanProduct.productCode() != ProductCode.UNSECURED_CONSUMER_LOAN
                || loanProduct.productType() != ProductType.UNSECURED) {
            throw new BusinessRuleViolationException(
                    "PRODUCT_POLICY_INVALID",
                    "Loan product is not configured as Unsecured Consumer Loan."
            );
        }
        if (!loanProduct.active()) {
            throw new BusinessRuleViolationException(
                    "PRODUCT_INACTIVE",
                    "Unsecured Consumer Loan product is inactive."
            );
        }
    }

    public void validateRequestedAmount(BigDecimal requestedAmount) {
        Objects.requireNonNull(requestedAmount, "requestedAmount must not be null");
        if (requestedAmount.compareTo(ZERO) <= 0
                || requestedAmount.compareTo(MINIMUM_AMOUNT) < 0
                || requestedAmount.compareTo(MAXIMUM_AMOUNT) > 0) {
            throw new BusinessRuleViolationException(
                    "INVALID_PRODUCT_AMOUNT",
                    "Requested amount is outside Unsecured Consumer Loan product limits."
            );
        }
        if (requestedAmount.remainder(BigDecimal.ONE).compareTo(ZERO) != 0) {
            throw new BusinessRuleViolationException(
                    "INVALID_PRODUCT_AMOUNT",
                    "Requested amount must be a whole VND amount."
            );
        }
    }

    public void validateRequestedTerm(int requestedTermMonths) {
        if (!ALLOWED_TERMS_MONTHS.contains(requestedTermMonths)) {
            throw new BusinessRuleViolationException(
                    "INVALID_PRODUCT_TERM",
                    "Requested term is not allowed for Unsecured Consumer Loan."
            );
        }
    }
}
