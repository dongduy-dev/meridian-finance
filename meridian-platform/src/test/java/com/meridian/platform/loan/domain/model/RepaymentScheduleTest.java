package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepaymentScheduleTest {

    @Test
    void acceptsOneImmutableFinalVersionOneAggregate() {
        ArrayList<RepaymentScheduleItem> source = new ArrayList<>(List.of(item(
                UUID.randomUUID(),
                1,
                LocalDate.of(2026, 8, 27),
                money(1_000),
                money(100)
        )));

        RepaymentSchedule schedule = schedule(
                RepaymentScheduleType.FINAL,
                1,
                1,
                money(1_000),
                money(100),
                money(1_100),
                source
        );
        source.clear();

        assertEquals(1, schedule.items().size());
        assertThrows(UnsupportedOperationException.class, () -> schedule.items().clear());
        assertTrue(schedule.toString().contains("financialEvidence=redacted"));
        assertFalse(schedule.items().getFirst().toString().contains("1000.00"));
    }

    @Test
    void rejectsUnsupportedTypeVersionCountSequenceSourcesDatesAndTotals() {
        RepaymentScheduleItem first = item(
                UUID.randomUUID(),
                1,
                LocalDate.of(2026, 8, 27),
                money(500),
                money(50)
        );
        RepaymentScheduleItem second = item(
                UUID.randomUUID(),
                2,
                LocalDate.of(2026, 9, 27),
                money(500),
                money(50)
        );

        assertThrows(BusinessRuleViolationException.class, () -> schedule(
                RepaymentScheduleType.FINAL,
                2,
                2,
                money(1_000),
                money(100),
                money(1_100),
                List.of(first, second)
        ));
        assertThrows(BusinessRuleViolationException.class, () -> schedule(
                RepaymentScheduleType.FINAL,
                1,
                3,
                money(1_000),
                money(100),
                money(1_100),
                List.of(first, second)
        ));
        assertThrows(BusinessRuleViolationException.class, () -> schedule(
                RepaymentScheduleType.FINAL,
                1,
                2,
                money(1_000),
                money(100),
                money(1_100),
                List.of(second, first)
        ));
        RepaymentScheduleItem duplicateSource = new RepaymentScheduleItem(
                UUID.randomUUID(),
                first.sourceLoanContractRepaymentItemId(),
                2,
                LocalDate.of(2026, 9, 27),
                money(500),
                money(50),
                money(0),
                money(550)
        );
        assertThrows(BusinessRuleViolationException.class, () -> schedule(
                RepaymentScheduleType.FINAL,
                1,
                2,
                money(1_000),
                money(100),
                money(1_100),
                List.of(first, duplicateSource)
        ));
        RepaymentScheduleItem nonIncreasing = item(
                UUID.randomUUID(),
                2,
                LocalDate.of(2026, 8, 27),
                money(500),
                money(50)
        );
        assertThrows(BusinessRuleViolationException.class, () -> schedule(
                RepaymentScheduleType.FINAL,
                1,
                2,
                money(1_000),
                money(100),
                money(1_100),
                List.of(first, nonIncreasing)
        ));
        assertThrows(BusinessRuleViolationException.class, () -> schedule(
                RepaymentScheduleType.FINAL,
                1,
                2,
                money(1_001),
                money(100),
                money(1_101),
                List.of(first, second)
        ));
    }

    @Test
    void rejectsInvalidItemMoneyAndInstallmentNumber() {
        assertThrows(BusinessRuleViolationException.class, () -> new RepaymentScheduleItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                LocalDate.now(),
                money(1_000),
                money(100),
                money(0),
                money(1_100)
        ));
        assertThrows(BusinessRuleViolationException.class, () -> new RepaymentScheduleItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                LocalDate.now(),
                new BigDecimal("1000.50"),
                money(100),
                money(0),
                new BigDecimal("1100.50")
        ));
        assertThrows(BusinessRuleViolationException.class, () -> new RepaymentScheduleItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                LocalDate.now(),
                money(1_000),
                money(100),
                money(0),
                money(1_101)
        ));
    }

    private static RepaymentSchedule schedule(
            RepaymentScheduleType type,
            int version,
            int term,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal total,
            List<RepaymentScheduleItem> items
    ) {
        return new RepaymentSchedule(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                type,
                version,
                term,
                principal,
                interest,
                money(0),
                total,
                items.getFirst().dueDate(),
                items.getLast().dueDate(),
                LocalDateTime.now(),
                items
        );
    }

    private static RepaymentScheduleItem item(
            UUID sourceId,
            int installment,
            LocalDate date,
            BigDecimal principal,
            BigDecimal interest
    ) {
        return new RepaymentScheduleItem(
                UUID.randomUUID(),
                sourceId,
                installment,
                date,
                principal,
                interest,
                money(0),
                principal.add(interest)
        );
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}
