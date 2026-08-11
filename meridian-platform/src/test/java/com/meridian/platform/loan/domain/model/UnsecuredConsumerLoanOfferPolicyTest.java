package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnsecuredConsumerLoanOfferPolicyTest {

    @Test
    void acceptsApprovedMvpPolicy() {
        assertDoesNotThrow(() -> policy());
    }

    @Test
    void requiresMonthlyInstallmentRepayment() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy(RepaymentMethod.ON_SALARY_DATE, money(0), 7, terms(), rate())
        );

        assertEquals("PRODUCT_POLICY_INVALID", exception.getErrorCode());
    }

    @Test
    void requiresZeroFee() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy(RepaymentMethod.MONTHLY_INSTALLMENT, money(1), 7, terms(), rate())
        );

        assertEquals("PRODUCT_POLICY_INVALID", exception.getErrorCode());
    }

    @Test
    void requiresPositiveOfferValidity() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy(RepaymentMethod.MONTHLY_INSTALLMENT, money(0), 0, terms(), rate())
        );

        assertEquals("PRODUCT_POLICY_INVALID", exception.getErrorCode());
    }

    @Test
    void requiresExactlyApprovedTerms() {
        BusinessRuleViolationException missing = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy(
                        RepaymentMethod.MONTHLY_INSTALLMENT,
                        money(0),
                        7,
                        Set.of(3, 6, 9),
                        rate()
                )
        );
        BusinessRuleViolationException extra = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy(
                        RepaymentMethod.MONTHLY_INSTALLMENT,
                        money(0),
                        7,
                        Set.of(3, 6, 9, 12, 18),
                        rate()
                )
        );

        assertEquals("PRODUCT_POLICY_INVALID", missing.getErrorCode());
        assertEquals("PRODUCT_POLICY_INVALID", extra.getErrorCode());
    }

    @Test
    void rejectsNegativeInterestRate() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy(
                        RepaymentMethod.MONTHLY_INSTALLMENT,
                        money(0),
                        7,
                        terms(),
                        new BigDecimal("-0.000001")
                )
        );

        assertEquals("PRODUCT_POLICY_INVALID", exception.getErrorCode());
    }

    @Test
    void rejectsUnsupportedApprovedTerm() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy().validateApprovedTerm(4)
        );

        assertEquals("INVALID_PRODUCT_TERM", exception.getErrorCode());
    }

    private UnsecuredConsumerLoanOfferPolicy policy() {
        return policy(RepaymentMethod.MONTHLY_INSTALLMENT, money(0), 7, terms(), rate());
    }

    private UnsecuredConsumerLoanOfferPolicy policy(
            RepaymentMethod repaymentMethod,
            BigDecimal fee,
            int validityDays,
            Set<Integer> allowedTerms,
            BigDecimal monthlyRate
    ) {
        return new UnsecuredConsumerLoanOfferPolicy(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                monthlyRate,
                fee,
                repaymentMethod,
                validityDays,
                allowedTerms
        );
    }

    private Set<Integer> terms() {
        return Set.of(3, 6, 9, 12);
    }

    private BigDecimal rate() {
        return new BigDecimal("0.018000");
    }

    private BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}
