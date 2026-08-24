package com.meridian.platform.loan.domain.model;

import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceOfferPolicy;
import com.meridian.platform.loan.domain.service.salaryadvance.SalaryAdvanceOfferCalculator;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApprovedOfferTest {

    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 7, 6, 8, 0);

    private final SalaryAdvanceOfferCalculator calculator = new SalaryAdvanceOfferCalculator();

    @Test
    void pendingOfferIsEffectivelyExpiredWhenNowEqualsExpiresAt() {
        ApprovedOffer offer = offer();

        assertEquals(ApprovedOfferStatus.PENDING, offer.effectiveStatusAt(offer.expiresAt().minusNanos(1)));
        assertEquals(ApprovedOfferStatus.EXPIRED, offer.effectiveStatusAt(offer.expiresAt()));
        assertEquals(ApprovedOfferStatus.EXPIRED, offer.effectiveStatusAt(offer.expiresAt().plusSeconds(1)));
    }

    @Test
    void acceptsPendingOffer() {
        LocalDateTime acceptedAt = GENERATED_AT.plusDays(1);
        ApprovedOffer accepted = offer().accept(acceptedAt);

        assertEquals(ApprovedOfferStatus.ACCEPTED, accepted.status());
        assertEquals(acceptedAt, accepted.acceptedAt());
    }

    @Test
    void declinesPendingOffer() {
        LocalDateTime declinedAt = GENERATED_AT.plusDays(1);
        ApprovedOffer declined = offer().decline(declinedAt);

        assertEquals(ApprovedOfferStatus.DECLINED, declined.status());
        assertEquals(declinedAt, declined.declinedAt());
    }

    @Test
    void expiresPendingOffer() {
        LocalDateTime expiredAt = GENERATED_AT.plusDays(7);
        ApprovedOffer expired = offer().expire(expiredAt);

        assertEquals(ApprovedOfferStatus.EXPIRED, expired.status());
        assertEquals(expiredAt, expired.expiredAt());
    }

    @Test
    void rejectsContradictoryTerminalTransition() {
        ApprovedOffer accepted = offer().accept(GENERATED_AT.plusDays(1));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> accepted.decline(GENERATED_AT.plusDays(2))
        );

        assertEquals("OFFER_ACTION_CONFLICT", exception.getErrorCode());
    }

    @Test
    void validatesRepaymentItemReconciliation() {
        ApprovedOffer currentOffer = offer();

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> new ApprovedOffer(
                        currentOffer.id(),
                        currentOffer.loanApplicationId(),
                        currentOffer.sourceLoanProductPolicyId(),
                        currentOffer.status(),
                        currentOffer.financialTerms(),
                        currentOffer.repaymentItems().stream()
                                .map(item -> item.installmentNumber() == 1
                                        ? new ProvisionalRepaymentItem(
                                                item.id(),
                                                item.installmentNumber(),
                                                item.principalDue().subtract(money(1)),
                                                item.interestDue(),
                                                item.feeDue(),
                                                item.totalDue().subtract(money(1))
                                        )
                                        : item)
                                .toList(),
                        currentOffer.generatedAt(),
                        currentOffer.expiresAt(),
                        currentOffer.acceptedAt(),
                        currentOffer.declinedAt(),
                        currentOffer.expiredAt()
                )
        );

        assertEquals("SYSTEM_STATE_CONFLICT", exception.getErrorCode());
    }

    private ApprovedOffer offer() {
        return calculator.generate(
                UUID.randomUUID(),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                new SalaryAdvanceOfferPolicy(
                        UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                        InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                        new BigDecimal("0.012000"),
                        money(0),
                        RepaymentMethod.ON_SALARY_DATE,
                        7,
                        Set.of(1, 2, 3)
                ),
                money(3_000_000),
                2,
                GENERATED_AT
        );
    }

    private BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}
