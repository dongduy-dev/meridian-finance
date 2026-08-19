package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import com.meridian.platform.loan.domain.model.CollateralLoanOfferPolicy;
import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollateralLoanOfferCalculatorTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID POLICY_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 8, 19, 9, 30);

    private final CollateralLoanOfferCalculator calculator = new CollateralLoanOfferCalculator();

    @ParameterizedTest
    @CsvSource({
            "6,450000,5450000",
            "12,900000,5900000",
            "18,1350000,6350000",
            "24,1800000,6800000"
    })
    void pricesEveryApprovedTerm(int term, long expectedInterest, long expectedRepayment) {
        ApprovedOffer offer = generate(money(5_000_000), term);

        assertEquals(ApprovedOfferStatus.PENDING, offer.status());
        assertEquals(money(5_000_000), offer.financialTerms().approvedPrincipal());
        assertEquals(term, offer.financialTerms().approvedTermMonths());
        assertEquals(InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                offer.financialTerms().interestCalculationMethod());
        assertEquals(new BigDecimal("0.015000"), offer.financialTerms().flatMonthlyInterestRate());
        assertEquals(money(expectedInterest), offer.financialTerms().totalInterest());
        assertEquals(money(0), offer.financialTerms().feeAmount());
        assertEquals(money(expectedRepayment), offer.financialTerms().totalRepaymentAmount());
        assertEquals(RepaymentMethod.MONTHLY_INSTALLMENT, offer.financialTerms().repaymentMethod());
        assertEquals(term, offer.repaymentItems().size());
        assertEquals(GENERATED_AT.plusDays(7), offer.expiresAt());
    }

    @Test
    void pricesApprovedExampleExactly() {
        ApprovedOffer offer = generate(money(12_000_000), 12);

        assertEquals(money(2_160_000), offer.financialTerms().totalInterest());
        assertEquals(money(14_160_000), offer.financialTerms().totalRepaymentAmount());
        offer.repaymentItems().forEach(item -> {
            assertEquals(money(1_000_000), item.principalDue());
            assertEquals(money(180_000), item.interestDue());
            assertEquals(money(0), item.feeDue());
            assertEquals(money(1_180_000), item.totalDue());
        });
    }

    @ParameterizedTest
    @ValueSource(longs = {5_000_000, 100_000_000})
    void pricesCollateralPrincipalBounds(long principal) {
        assertEquals(money(principal), generate(money(principal), 12).financialTerms().approvedPrincipal());
    }

    @Test
    void roundsTotalInterestOnceHalfUpToWholeVnd() {
        ApprovedOffer offer = generate(money(50), 6);

        assertEquals(money(5), offer.financialTerms().totalInterest());
    }

    @Test
    void assignsPrincipalAndInterestRemaindersOnlyToFinalInstallment() {
        ApprovedOffer offer = generate(money(5_000_011), 6);

        for (int index = 0; index < 5; index++) {
            assertEquals(money(833_335), offer.repaymentItems().get(index).principalDue());
            assertEquals(money(75_000), offer.repaymentItems().get(index).interestDue());
        }
        assertEquals(money(833_336), offer.repaymentItems().getLast().principalDue());
        assertEquals(money(75_001), offer.repaymentItems().getLast().interestDue());
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 12, 18, 24})
    void everyTermReconcilesWithoutRoundingDrift(int term) {
        ApprovedOffer offer = generate(money(5_000_011), term);

        assertEquals(offer.financialTerms().approvedPrincipal(), offer.repaymentItems().stream()
                .map(item -> item.principalDue()).reduce(money(0), BigDecimal::add));
        assertEquals(offer.financialTerms().totalInterest(), offer.repaymentItems().stream()
                .map(item -> item.interestDue()).reduce(money(0), BigDecimal::add));
        assertEquals(money(0), offer.repaymentItems().stream()
                .map(item -> item.feeDue()).reduce(money(0), BigDecimal::add));
        assertEquals(offer.financialTerms().totalRepaymentAmount(), offer.repaymentItems().stream()
                .map(item -> item.totalDue()).reduce(money(0), BigDecimal::add));
        assertTrue(offer.repaymentItems().stream().allMatch(item ->
                item.principalDue().remainder(BigDecimal.ONE).signum() == 0
                        && item.interestDue().remainder(BigDecimal.ONE).signum() == 0
                        && item.feeDue().remainder(BigDecimal.ONE).signum() == 0
                        && item.totalDue().remainder(BigDecimal.ONE).signum() == 0));
    }

    @Test
    void producesDeterministicFinancialResults() {
        ApprovedOffer first = generate(money(12_000_001), 18);
        ApprovedOffer second = generate(money(12_000_001), 18);

        assertEquals(first.financialTerms(), second.financialTerms());
        for (int index = 0; index < first.repaymentItems().size(); index++) {
            assertEquals(first.repaymentItems().get(index).principalDue(),
                    second.repaymentItems().get(index).principalDue());
            assertEquals(first.repaymentItems().get(index).interestDue(),
                    second.repaymentItems().get(index).interestDue());
            assertEquals(first.repaymentItems().get(index).totalDue(),
                    second.repaymentItems().get(index).totalDue());
        }
    }

    @Test
    void rejectsUnsupportedTerm() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> generate(money(5_000_000), 9)
        );

        assertEquals("INVALID_PRODUCT_TERM", exception.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveOfferValidity(int validityDays) {
        assertInvalidPolicy(
                InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.015000"),
                money(0),
                RepaymentMethod.MONTHLY_INSTALLMENT,
                validityDays,
                Set.of(6, 12, 18, 24)
        );
    }

    @Test
    void usesPositiveNonDefaultConfiguredOfferValidity() {
        CollateralLoanOfferPolicy policy = policy(30);

        ApprovedOffer offer = calculator.generate(
                UUID.randomUUID(),
                APPLICATION_ID,
                policy,
                money(5_000_000),
                12,
                GENERATED_AT
        );

        assertEquals(30, policy.offerValidityDays());
        assertEquals(GENERATED_AT.plusDays(30), offer.expiresAt());
    }

    @Test
    void rejectsInvalidPolicyVariants() {
        assertInvalidPolicy(
                InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.014999"),
                money(0),
                RepaymentMethod.MONTHLY_INSTALLMENT,
                7,
                Set.of(6, 12, 18, 24)
        );
        assertInvalidPolicy(
                InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.015000"),
                money(1),
                RepaymentMethod.MONTHLY_INSTALLMENT,
                7,
                Set.of(6, 12, 18, 24)
        );
        assertInvalidPolicy(
                InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.015000"),
                money(0),
                RepaymentMethod.ON_SALARY_DATE,
                7,
                Set.of(6, 12, 18, 24)
        );
        assertInvalidPolicy(
                InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.015000"),
                money(0),
                RepaymentMethod.MONTHLY_INSTALLMENT,
                7,
                Set.of(6, 12, 18)
        );
    }

    private ApprovedOffer generate(BigDecimal principal, int term) {
        return calculator.generate(
                UUID.randomUUID(),
                APPLICATION_ID,
                policy(),
                principal,
                term,
                GENERATED_AT
        );
    }

    private CollateralLoanOfferPolicy policy() {
        return policy(7);
    }

    private CollateralLoanOfferPolicy policy(int validityDays) {
        return new CollateralLoanOfferPolicy(
                POLICY_ID,
                InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.015000"),
                money(0),
                RepaymentMethod.MONTHLY_INSTALLMENT,
                validityDays,
                Set.of(6, 12, 18, 24)
        );
    }

    private void assertInvalidPolicy(
            InterestCalculationMethod method,
            BigDecimal rate,
            BigDecimal fee,
            RepaymentMethod repaymentMethod,
            int validityDays,
            Set<Integer> terms
    ) {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> new CollateralLoanOfferPolicy(
                        POLICY_ID, method, rate, fee, repaymentMethod, validityDays, terms
                )
        );
        assertEquals("PRODUCT_POLICY_INVALID", exception.getErrorCode());
    }

    private BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}
