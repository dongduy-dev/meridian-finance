package com.meridian.platform.loan.domain.model.salaryadvance;

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

    @Test
    void releasesUsedExposureExactlyForEveryLimitStatus() {
        LocalDateTime refreshedAt = LocalDateTime.of(2026, 7, 1, 8, 0);
        for (SalaryAdvanceLimitStatus status : SalaryAdvanceLimitStatus.values()) {
            SalaryAdvanceLimit original = new SalaryAdvanceLimit(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    money(2_000), money(1_200), money(200), money(600),
                    status, refreshedAt
            );

            SalaryAdvanceLimit released = original.releaseUsed(money(300));

            assertEquals(money(900), released.usedAmount());
            assertEquals(money(900), released.availableAmount());
            assertEquals(money(200), released.reservedAmount());
            assertEquals(money(2_000), released.totalLimit());
            assertEquals(refreshedAt, released.lastRefreshedAt());
            assertEquals(status, released.status());
        }
    }

    @Test
    void rejectsInvalidOrExcessiveUsedExposureRelease() {
        SalaryAdvanceLimit limit = limit(
                SalaryAdvanceLimitStatus.DISABLED,
                money(2_000), money(300), money(200), money(1_500)
        );

        assertThrows(BusinessRuleViolationException.class,
                () -> limit.releaseUsed(money(301)));
        assertThrows(BusinessRuleViolationException.class,
                () -> limit.releaseUsed(money(0)));
        assertThrows(BusinessRuleViolationException.class,
                () -> limit.releaseUsed(money(-1)));
        assertThrows(BusinessRuleViolationException.class,
                () -> limit.releaseUsed(new BigDecimal("1.50")));
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
