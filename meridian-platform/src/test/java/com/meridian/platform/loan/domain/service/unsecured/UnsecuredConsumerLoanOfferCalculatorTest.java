package com.meridian.platform.loan.domain.service.unsecured;

import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanOfferPolicy;
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

class UnsecuredConsumerLoanOfferCalculatorTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 8, 11, 9, 30);

    private final UnsecuredConsumerLoanOfferCalculator calculator = new UnsecuredConsumerLoanOfferCalculator();

    @ParameterizedTest
    @CsvSource({
            "3,270000,5270000",
            "6,540000,5540000",
            "9,810000,5810000",
            "12,1080000,6080000"
    })
    void pricesEveryApprovedTerm(int term, long expectedInterest, long expectedRepayment) {
        ApprovedOffer offer = generate(money(5_000_000), term);

        assertEquals(ApprovedOfferStatus.PENDING, offer.status());
        assertEquals(money(5_000_000), offer.financialTerms().approvedPrincipal());
        assertEquals(term, offer.financialTerms().approvedTermMonths());
        assertEquals(InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                offer.financialTerms().interestCalculationMethod());
        assertEquals(new BigDecimal("0.018000"), offer.financialTerms().flatMonthlyInterestRate());
        assertEquals(money(expectedInterest), offer.financialTerms().totalInterest());
        assertEquals(money(0), offer.financialTerms().feeAmount());
        assertEquals(money(expectedRepayment), offer.financialTerms().totalRepaymentAmount());
        assertEquals(RepaymentMethod.MONTHLY_INSTALLMENT, offer.financialTerms().repaymentMethod());
        assertEquals(term, offer.repaymentItems().size());
        assertEquals(GENERATED_AT.plusDays(7), offer.expiresAt());
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 6, 9, 12})
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
    void roundsTotalInterestHalfUpToWholeVnd() {
        ApprovedOffer offer = generate(money(250), 3);

        assertEquals(money(14), offer.financialTerms().totalInterest());
    }

    @Test
    void assignsPrincipalAndInterestRemaindersOnlyToFinalInstallment() {
        ApprovedOffer offer = generate(money(5_000_011), 6);

        for (int index = 0; index < 5; index++) {
            assertEquals(money(833_335), offer.repaymentItems().get(index).principalDue());
            assertEquals(money(90_000), offer.repaymentItems().get(index).interestDue());
        }
        assertEquals(money(833_336), offer.repaymentItems().getLast().principalDue());
        assertEquals(money(90_001), offer.repaymentItems().getLast().interestDue());
    }

    @Test
    void rejectsUnsupportedTerm() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> generate(money(5_000_000), 4)
        );

        assertEquals("INVALID_PRODUCT_TERM", exception.getErrorCode());
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

    private UnsecuredConsumerLoanOfferPolicy policy() {
        return new UnsecuredConsumerLoanOfferPolicy(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.018000"),
                money(0),
                RepaymentMethod.MONTHLY_INSTALLMENT,
                7,
                Set.of(3, 6, 9, 12)
        );
    }

    private BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}
