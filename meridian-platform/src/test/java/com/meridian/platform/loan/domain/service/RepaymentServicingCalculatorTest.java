package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.RepaymentAllocation;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleItem;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepaymentServicingCalculatorTest {
    private final DeterministicRepaymentAllocator allocator =
            new DeterministicRepaymentAllocator();
    private final RepaymentServicingCalculator calculator =
            new RepaymentServicingCalculator();

    @Test
    void updatesComponentsAndReevaluatesEveryInstallmentAtRecordingDate() {
        RepaymentSchedule schedule = schedule();
        List<RepaymentInstallmentProgress> before = initial(schedule, LocalDate.of(2026, 7, 1));
        UUID transactionId = UUID.randomUUID();
        List<RepaymentAllocation> allocations = allocator.allocate(
                transactionId, money("1200"), schedule, before
        );

        RepaymentServicingCalculator.Result result = calculator.apply(
                schedule, before, allocations, LocalDate.of(2026, 8, 15),
                LocalDateTime.of(2026, 10, 2, 9, 0), LocalDate.of(2026, 10, 2)
        );

        assertEquals(money("1200"), result.balance().totalPaid());
        assertEquals(money("1020"), result.balance().totalOutstanding());
        assertEquals(LoanAccountStatus.OVERDUE, result.accountStatus());
        assertEquals(RepaymentInstallmentStatus.PAID, result.progress().get(0).status());
        assertEquals(RepaymentInstallmentStatus.OVERDUE, result.progress().get(1).status());
        assertEquals(List.of(schedule.items().get(0).id(), schedule.items().get(1).id()),
                result.installmentStatusChanges());
    }

    @Test
    void preservesMaximumValueDateAndLatestRecordedTimestampForBackdatedPayment() {
        RepaymentSchedule schedule = schedule();
        List<RepaymentInstallmentProgress> initial = initial(
                schedule, LocalDate.of(2026, 7, 1)
        );
        List<RepaymentAllocation> firstAllocations = allocator.allocate(
                UUID.randomUUID(), money("100"), schedule, initial
        );
        RepaymentServicingCalculator.Result first = calculator.apply(
                schedule, initial, firstAllocations, LocalDate.of(2026, 9, 15),
                LocalDateTime.of(2026, 9, 20, 10, 0), LocalDate.of(2026, 9, 20)
        );
        List<RepaymentAllocation> secondAllocations = allocator.allocate(
                UUID.randomUUID(), money("100"), schedule, first.progress()
        );

        RepaymentServicingCalculator.Result second = calculator.apply(
                schedule, first.progress(), secondAllocations, LocalDate.of(2026, 8, 15),
                LocalDateTime.of(2026, 9, 21, 10, 0), LocalDate.of(2026, 9, 21)
        );

        assertEquals(LocalDate.of(2026, 9, 15),
                second.balance().lastPaymentValueDate());
        assertEquals(LocalDateTime.of(2026, 9, 21, 10, 0),
                second.balance().lastPaymentRecordedAt());
    }

    @Test
    void exactContractualPayoffSettlesAccount() {
        RepaymentSchedule schedule = schedule();
        List<RepaymentInstallmentProgress> before = initial(schedule, LocalDate.of(2026, 7, 1));
        List<RepaymentAllocation> allocations = allocator.allocate(
                UUID.randomUUID(), schedule.totalRepaymentAmount(), schedule, before
        );

        RepaymentServicingCalculator.Result result = calculator.apply(
                schedule, before, allocations, LocalDate.of(2026, 7, 2),
                LocalDateTime.of(2026, 7, 2, 10, 0), LocalDate.of(2026, 7, 2)
        );

        assertEquals(money("0"), result.balance().totalOutstanding());
        assertEquals(LoanAccountStatus.SETTLED, result.accountStatus());
        assertEquals(2, result.progress().stream()
                .filter(item -> item.status() == RepaymentInstallmentStatus.PAID).count());
    }

    @Test
    void partialEarlyRepaymentKeepsAccountActive() {
        RepaymentSchedule schedule = schedule();
        List<RepaymentInstallmentProgress> before = initial(
                schedule, LocalDate.of(2026, 7, 1)
        );
        List<RepaymentAllocation> allocations = allocator.allocate(
                UUID.randomUUID(), money("100"), schedule, before
        );

        RepaymentServicingCalculator.Result result = calculator.apply(
                schedule, before, allocations, LocalDate.of(2026, 7, 2),
                LocalDateTime.of(2026, 7, 2, 10, 0), LocalDate.of(2026, 7, 2)
        );

        assertEquals(LoanAccountStatus.ACTIVE, result.accountStatus());
        assertEquals(RepaymentInstallmentStatus.PARTIALLY_PAID,
                result.progress().getFirst().status());
        assertEquals(RepaymentInstallmentStatus.NOT_DUE,
                result.progress().getLast().status());
    }

    @Test
    void payingPastDueInstallmentCuresOverdueAccountToActive() {
        RepaymentSchedule schedule = schedule();
        List<RepaymentInstallmentProgress> initial = initial(
                schedule, LocalDate.of(2026, 7, 1)
        );
        RepaymentServicingCalculator.Result overdue = calculator.apply(
                schedule, initial, List.of(), LocalDate.of(2026, 9, 1),
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDate.of(2026, 9, 1)
        );
        assertEquals(LoanAccountStatus.OVERDUE, overdue.accountStatus());
        List<RepaymentAllocation> cure = allocator.allocate(
                UUID.randomUUID(), schedule.items().getFirst().totalDue(),
                schedule, overdue.progress()
        );

        RepaymentServicingCalculator.Result cured = calculator.apply(
                schedule, overdue.progress(), cure, LocalDate.of(2026, 9, 1),
                LocalDateTime.of(2026, 9, 1, 10, 0), LocalDate.of(2026, 9, 1)
        );

        assertEquals(LoanAccountStatus.ACTIVE, cured.accountStatus());
        assertEquals(RepaymentInstallmentStatus.PAID,
                cured.progress().getFirst().status());
        assertEquals(RepaymentInstallmentStatus.NOT_DUE,
                cured.progress().getLast().status());
    }
    private static List<RepaymentInstallmentProgress> initial(
            RepaymentSchedule schedule,
            LocalDate evaluationDate
    ) {
        return schedule.items().stream().map(item -> RepaymentInstallmentProgress.initial(
                schedule, item, evaluationDate, evaluationDate.atStartOfDay()
        )).toList();
    }

    private static RepaymentSchedule schedule() {
        LocalDate firstDue = LocalDate.of(2026, 8, 27);
        LocalDate secondDue = LocalDate.of(2026, 9, 27);
        return new RepaymentSchedule(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                RepaymentScheduleType.FINAL, 1, 2, money("2000"), money("200"),
                money("20"), money("2220"), firstDue, secondDue,
                LocalDateTime.of(2026, 7, 1, 10, 0),
                List.of(item(1, firstDue), item(2, secondDue))
        );
    }

    private static RepaymentScheduleItem item(int number, LocalDate dueDate) {
        return new RepaymentScheduleItem(
                UUID.randomUUID(), UUID.randomUUID(), number, dueDate,
                money("1000"), money("100"), money("10"), money("1110")
        );
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
