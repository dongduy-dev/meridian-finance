package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnsecuredConsumerLoanApplicationPolicyTest {

    private final UnsecuredConsumerLoanApplicationPolicy policy =
            new UnsecuredConsumerLoanApplicationPolicy();

    @Test
    void acceptsMinimumAndMaximumWholeVndAmounts() {
        assertDoesNotThrow(() -> policy.validateRequestedAmount(new BigDecimal("2000000")));
        assertDoesNotThrow(() -> policy.validateRequestedAmount(new BigDecimal("50000000.00")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1999999", "50000001", "2000000.01"})
    void rejectsAmountsOutsideBoundsOrWithFractionalVnd(String amount) {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validateRequestedAmount(new BigDecimal(amount))
        );

        assertEquals("INVALID_PRODUCT_AMOUNT", exception.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 6, 9, 12})
    void acceptsApprovedTerms(int termMonths) {
        assertDoesNotThrow(() -> policy.validateRequestedTerm(termMonths));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 18})
    void rejectsUnsupportedTerms(int termMonths) {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validateRequestedTerm(termMonths)
        );

        assertEquals("INVALID_PRODUCT_TERM", exception.getErrorCode());
    }

    @Test
    void requiresActiveUnsecuredConsumerLoanProduct() {
        assertDoesNotThrow(() -> policy.validateProduct(product(true)));

        BusinessRuleViolationException inactive = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validateProduct(product(false))
        );
        assertEquals("PRODUCT_INACTIVE", inactive.getErrorCode());

        LoanProduct wrongProduct = new LoanProduct(
                UUID.randomUUID(),
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                "Salary Advance",
                null,
                true,
                new BigDecimal("500000"),
                new BigDecimal("10000000")
        );
        BusinessRuleViolationException invalid = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validateProduct(wrongProduct)
        );
        assertEquals("PRODUCT_POLICY_INVALID", invalid.getErrorCode());

        LoanProduct wrongType = new LoanProduct(
                UUID.randomUUID(),
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.SALARY_BASED,
                "Misconfigured Unsecured Consumer Loan",
                null,
                true,
                new BigDecimal("2000000"),
                new BigDecimal("50000000")
        );
        BusinessRuleViolationException invalidType = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validateProduct(wrongType)
        );
        assertEquals("PRODUCT_POLICY_INVALID", invalidType.getErrorCode());
    }

    private LoanProduct product(boolean active) {
        return new LoanProduct(
                UUID.randomUUID(),
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                "Unsecured Consumer Loan",
                null,
                active,
                new BigDecimal("2000000"),
                new BigDecimal("50000000")
        );
    }
}
