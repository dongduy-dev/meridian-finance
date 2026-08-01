package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepaymentBalanceTest {

    @Test
    void createsImmutableZeroPaidFullOutstandingBalance() {
        RepaymentBalance balance = RepaymentBalance.initial(
                money("1000"),
                money("120"),
                money("0"),
                LocalDate.of(2026, 7, 27)
        );

        assertEquals(money("0"), balance.totalPaid());
        assertEquals(money("1120"), balance.totalOutstanding());
        balance.validateAgainst(money("1000"), money("120"), money("0"));
    }

    @Test
    void rejectsFractionalNegativeAndUnreconciledEvidence() {
        assertThrows(BusinessRuleViolationException.class, () -> new RepaymentBalance(
                money("100"),
                money("0"),
                money("0"),
                money("99"),
                money("900"),
                money("100"),
                money("0"),
                money("1000"),
                LocalDate.of(2026, 7, 27),
                LocalDateTime.of(2026, 7, 27, 10, 0),
                LocalDate.of(2026, 7, 27)
        ));
        assertThrows(BusinessRuleViolationException.class, () ->
                RepaymentBalance.initial(
                        new BigDecimal("1000.50"),
                        money("0"),
                        money("0"),
                        LocalDate.of(2026, 7, 27)
                ));
        RepaymentBalance balance = RepaymentBalance.initial(
                money("1000"),
                money("100"),
                money("0"),
                LocalDate.of(2026, 7, 27)
        );
        assertThrows(BusinessRuleViolationException.class, () ->
                balance.validateAgainst(money("999"), money("100"), money("0")));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
