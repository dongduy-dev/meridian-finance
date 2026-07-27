package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SalaryAdvanceLimitTest {

    @Test
    void convertsReservedExposureToUsedWithoutChangingAvailableForEveryStatus() {
        for (SalaryAdvanceLimitStatus status : SalaryAdvanceLimitStatus.values()) {
            SalaryAdvanceLimit original = limit(status, money(2_000), money(200), money(1_000), money(800));

            SalaryAdvanceLimit converted = original.convertReservedToUsed(money(1_000));

            assertEquals(money(1_200), converted.usedAmount());
            assertEquals(money(0), converted.reservedAmount());
            assertEquals(money(800), converted.availableAmount());
            assertEquals(money(2_000), converted.totalLimit());
            assertEquals(status, converted.status());
        }
    }

    @Test
    void rejectsInsufficientZeroNegativeFractionalAndUnreconciledConversion() {
        SalaryAdvanceLimit valid = limit(
                SalaryAdvanceLimitStatus.SUSPENDED,
                money(2_000),
                money(0),
                money(1_000),
                money(1_000)
        );

        assertThrows(
                BusinessRuleViolationException.class,
                () -> valid.convertReservedToUsed(money(1_001))
        );
        assertThrows(
                BusinessRuleViolationException.class,
                () -> valid.convertReservedToUsed(money(0))
        );
        assertThrows(
                BusinessRuleViolationException.class,
                () -> valid.convertReservedToUsed(money(-1))
        );
        assertThrows(
                BusinessRuleViolationException.class,
                () -> valid.convertReservedToUsed(new BigDecimal("1.50"))
        );
        SalaryAdvanceLimit invalid = limit(
                SalaryAdvanceLimitStatus.ACTIVE,
                money(2_000),
                money(0),
                money(1_000),
                money(999)
        );
        assertThrows(
                BusinessRuleViolationException.class,
                () -> invalid.convertReservedToUsed(money(1_000))
        );
    }

    private static SalaryAdvanceLimit limit(
            SalaryAdvanceLimitStatus status,
            BigDecimal total,
            BigDecimal used,
            BigDecimal reserved,
            BigDecimal available
    ) {
        return new SalaryAdvanceLimit(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                total,
                used,
                reserved,
                available,
                status,
                LocalDateTime.now()
        );
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}
