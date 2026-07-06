package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
import com.meridian.platform.loan.domain.model.SalaryAdvanceOfferPolicy;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SalaryAdvanceOfferCalculatorTest {

    private static final UUID OFFER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID POLICY_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 7, 6, 8, 0);

    private final SalaryAdvanceOfferCalculator calculator = new SalaryAdvanceOfferCalculator();

    @Test
    void pricesOneMonthSalaryAdvanceExactly() {
        ApprovedOffer offer = calculator.generate(
                OFFER_ID,
                LOAN_APPLICATION_ID,
                policy(),
                money(3_000_000),
                1,
                GENERATED_AT
        );

        assertEquals(money(3_000_000), offer.financialTerms().approvedPrincipal());
        assertEquals(1, offer.financialTerms().approvedTermMonths());
        assertEquals(money(36_000), offer.financialTerms().totalInterest());
        assertEquals(money(0), offer.financialTerms().feeAmount());
        assertEquals(money(3_036_000), offer.financialTerms().totalRepaymentAmount());
        assertEquals(GENERATED_AT.plusDays(7), offer.expiresAt());
        assertEquals(1, offer.repaymentItems().size());
        assertEquals(money(3_000_000), offer.repaymentItems().get(0).principalDue());
        assertEquals(money(36_000), offer.repaymentItems().get(0).interestDue());
    }

    @Test
    void pricesTwoAndThreeMonthSalaryAdvanceTerms() {
        ApprovedOffer twoMonth = calculator.generate(
                UUID.randomUUID(),
                LOAN_APPLICATION_ID,
                policy(),
                money(3_000_000),
                2,
                GENERATED_AT
        );
        ApprovedOffer threeMonth = calculator.generate(
                UUID.randomUUID(),
                LOAN_APPLICATION_ID,
                policy(),
                money(3_000_000),
                3,
                GENERATED_AT
        );

        assertEquals(money(72_000), twoMonth.financialTerms().totalInterest());
        assertEquals(money(3_072_000), twoMonth.financialTerms().totalRepaymentAmount());
        assertEquals(2, twoMonth.repaymentItems().size());
        assertEquals(money(108_000), threeMonth.financialTerms().totalInterest());
        assertEquals(money(3_108_000), threeMonth.financialTerms().totalRepaymentAmount());
        assertEquals(3, threeMonth.repaymentItems().size());
    }

    @Test
    void roundsTotalInterestHalfUpToWholeVnd() {
        ApprovedOffer roundsDown = calculator.generate(
                UUID.randomUUID(),
                LOAN_APPLICATION_ID,
                policy(),
                money(41),
                1,
                GENERATED_AT
        );
        ApprovedOffer roundsUp = calculator.generate(
                UUID.randomUUID(),
                LOAN_APPLICATION_ID,
                policy(),
                money(42),
                1,
                GENERATED_AT
        );

        assertEquals(money(0), roundsDown.financialTerms().totalInterest());
        assertEquals(money(1), roundsUp.financialTerms().totalInterest());
    }

    @Test
    void finalItemsAbsorbPrincipalAndInterestRemainders() {
        ApprovedOffer offer = calculator.generate(
                UUID.randomUUID(),
                LOAN_APPLICATION_ID,
                policy(),
                money(1_000_001),
                2,
                GENERATED_AT
        );

        assertEquals(money(500_000), offer.repaymentItems().get(0).principalDue());
        assertEquals(money(500_001), offer.repaymentItems().get(1).principalDue());
        assertEquals(money(12_000), offer.repaymentItems().get(0).interestDue());
        assertEquals(money(12_000), offer.repaymentItems().get(1).interestDue());
        assertEquals(money(1_024_001), offer.financialTerms().totalRepaymentAmount());
    }

    @Test
    void finalInterestItemAbsorbsInterestRemainder() {
        ApprovedOffer offer = calculator.generate(
                UUID.randomUUID(),
                LOAN_APPLICATION_ID,
                policy(),
                money(1_000_030),
                2,
                GENERATED_AT
        );

        assertEquals(money(24_001), offer.financialTerms().totalInterest());
        assertEquals(money(12_000), offer.repaymentItems().get(0).interestDue());
        assertEquals(money(12_001), offer.repaymentItems().get(1).interestDue());
    }

    @Test
    void rejectsTermsOutsideSalaryAdvancePolicy() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> calculator.generate(
                        UUID.randomUUID(),
                        LOAN_APPLICATION_ID,
                        policy(),
                        money(3_000_000),
                        4,
                        GENERATED_AT
                )
        );

        assertEquals("INVALID_PRODUCT_TERM", exception.getErrorCode());
    }

    private SalaryAdvanceOfferPolicy policy() {
        return new SalaryAdvanceOfferPolicy(
                POLICY_ID,
                InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.012000"),
                money(0),
                RepaymentMethod.ON_SALARY_DATE,
                7,
                Set.of(1, 2, 3)
        );
    }

    private BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}
