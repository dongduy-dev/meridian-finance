package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.RepaymentAllocation;
import com.meridian.platform.loan.domain.model.RepaymentAllocationComponent;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleItem;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeterministicRepaymentAllocatorTest {

    private final DeterministicRepaymentAllocator allocator =
            new DeterministicRepaymentAllocator();

    @Test
    void appliesG1ComponentsAndG2OldestInstallmentBeforeFutureItems() {
        RepaymentSchedule schedule = schedule();
        List<RepaymentInstallmentProgress> progress = progress(schedule);
        UUID transactionId = UUID.randomUUID();

        List<RepaymentAllocation> allocations = allocator.allocate(
                transactionId,
                money("700"),
                schedule,
                List.of(progress.get(1), progress.get(0))
        );

        assertEquals(3, allocations.size());
        assertEquals(RepaymentAllocationComponent.FEE,
                allocations.get(0).component());
        assertEquals(money("10"), allocations.get(0).amount());
        assertEquals(RepaymentAllocationComponent.INTEREST,
                allocations.get(1).component());
        assertEquals(money("100"), allocations.get(1).amount());
        assertEquals(RepaymentAllocationComponent.PRINCIPAL,
                allocations.get(2).component());
        assertEquals(money("590"), allocations.get(2).amount());
        assertEquals(schedule.items().get(0).id(),
                allocations.get(0).repaymentScheduleItemId());
    }

    @Test
    void allowsG3EarlyAllocationAndPartialPaymentWithoutScheduleMutation() {
        RepaymentSchedule schedule = schedule();
        List<RepaymentInstallmentProgress> progress = progress(schedule);
        BigDecimal originalFirstPrincipal = schedule.items().get(0).principalDue();

        List<RepaymentAllocation> allocations = allocator.allocate(
                UUID.randomUUID(),
                money("1200"),
                schedule,
                progress
        );

        assertEquals(5, allocations.size());
        assertEquals(schedule.items().get(1).id(),
                allocations.getLast().repaymentScheduleItemId());
        assertEquals(RepaymentAllocationComponent.INTEREST,
                allocations.getLast().component());
        assertEquals(originalFirstPrincipal,
                schedule.items().get(0).principalDue());
        assertThrows(UnsupportedOperationException.class, allocations::clear);
    }

    @Test
    void rejectsG4OverpaymentAndNonWholeVnd() {
        RepaymentSchedule schedule = schedule();
        List<RepaymentInstallmentProgress> progress = progress(schedule);

        assertThrows(BusinessRuleViolationException.class, () -> allocator.allocate(
                UUID.randomUUID(),
                money("2221"),
                schedule,
                progress
        ));
        assertThrows(BusinessRuleViolationException.class, () -> allocator.allocate(
                UUID.randomUUID(),
                new BigDecimal("100.50"),
                schedule,
                progress
        ));
    }

    private static List<RepaymentInstallmentProgress> progress(
            RepaymentSchedule schedule
    ) {
        return schedule.items().stream()
                .map(item -> RepaymentInstallmentProgress.initial(
                        schedule,
                        item,
                        LocalDate.of(2026, 7, 27),
                        LocalDateTime.of(2026, 7, 27, 10, 0)
                ))
                .toList();
    }

    private static RepaymentSchedule schedule() {
        LocalDate firstDue = LocalDate.of(2026, 8, 27);
        LocalDate secondDue = LocalDate.of(2026, 9, 27);
        return new RepaymentSchedule(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                RepaymentScheduleType.FINAL,
                1,
                2,
                money("2000"),
                money("200"),
                money("20"),
                money("2220"),
                firstDue,
                secondDue,
                LocalDateTime.of(2026, 7, 27, 10, 0),
                List.of(
                        item(1, firstDue),
                        item(2, secondDue)
                )
        );
    }

    private static RepaymentScheduleItem item(int installment, LocalDate dueDate) {
        return new RepaymentScheduleItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                installment,
                dueDate,
                money("1000"),
                money("100"),
                money("10"),
                money("1110")
        );
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
