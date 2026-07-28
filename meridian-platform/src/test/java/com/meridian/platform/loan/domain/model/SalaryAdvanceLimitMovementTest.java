package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SalaryAdvanceLimitMovementTest {

    @Test
    void createsDisbursedToUsedMovementWithApplicationAndAccountReferences() {
        UUID limitId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        SalaryAdvanceLimitMovement movement = SalaryAdvanceLimitMovement.disbursedToUsed(
                UUID.randomUUID(),
                limitId,
                applicationId,
                accountId,
                money(1_000),
                LocalDateTime.of(2026, 7, 27, 10, 0)
        );

        assertEquals(limitId, movement.salaryAdvanceLimitId());
        assertEquals(applicationId, movement.loanApplicationId());
        assertEquals(accountId, movement.loanAccountId());
        assertEquals(SalaryAdvanceLimitMovementType.DISBURSED_TO_USED, movement.movementType());
        assertEquals(money(1_000), movement.amount());
    }

    @Test
    void requiresApplicationAccountAndPositiveWholeVndAmount() {
        assertThrows(NullPointerException.class, () -> SalaryAdvanceLimitMovement.disbursedToUsed(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                money(1_000),
                LocalDateTime.now()
        ));
        assertThrows(NullPointerException.class, () -> SalaryAdvanceLimitMovement.disbursedToUsed(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                money(1_000),
                LocalDateTime.now()
        ));
        assertThrows(BusinessRuleViolationException.class, () -> SalaryAdvanceLimitMovement.disbursedToUsed(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                money(0),
                LocalDateTime.now()
        ));
        assertThrows(BusinessRuleViolationException.class, () -> SalaryAdvanceLimitMovement.disbursedToUsed(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("1000.50"),
                LocalDateTime.now()
        ));
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}
