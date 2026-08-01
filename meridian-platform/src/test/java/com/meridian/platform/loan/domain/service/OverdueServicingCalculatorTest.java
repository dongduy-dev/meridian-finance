package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.RepaymentBalance;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleItem;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OverdueServicingCalculatorTest {

    private final OverdueServicingCalculator calculator = new OverdueServicingCalculator();

    @Test
    void advancesUnpaidInstallmentFromNotDueToDueToOverdueAtUtcDateBoundaries() {
        RepaymentSchedule schedule = schedule();
        LocalDate activationDate = LocalDate.of(2026, 7, 1);
        List<RepaymentInstallmentProgress> initial = initial(schedule, activationDate);
        LoanAccount account = account(schedule, LoanAccountStatus.ACTIVE,
                RepaymentBalance.initial(money("2000"), money("200"), money("20"), activationDate));

        OverdueServicingCalculator.Result before = evaluate(account, schedule, initial,
                LocalDate.of(2026, 8, 26));
        assertEquals(List.of(RepaymentInstallmentStatus.NOT_DUE,
                RepaymentInstallmentStatus.NOT_DUE), statuses(before));
        assertEquals(LoanAccountStatus.ACTIVE, before.accountStatus());

        OverdueServicingCalculator.Result due = evaluate(
                account.withServicingState(before.balance(), before.accountStatus(), at("2026-08-26T00:00:00")),
                schedule, before.progress(), LocalDate.of(2026, 8, 27));
        assertEquals(RepaymentInstallmentStatus.DUE, due.progress().getFirst().status());
        assertEquals(LoanAccountStatus.ACTIVE, due.accountStatus());

        OverdueServicingCalculator.Result overdue = evaluate(
                account.withServicingState(due.balance(), due.accountStatus(), at("2026-08-27T00:00:00")),
                schedule, due.progress(), LocalDate.of(2026, 8, 28));
        assertEquals(RepaymentInstallmentStatus.OVERDUE, overdue.progress().getFirst().status());
        assertEquals(LoanAccountStatus.OVERDUE, overdue.accountStatus());
    }

    @Test
    void partiallyPaidInstallmentStaysPartialThroughDueDateThenBecomesOverdue() {
        RepaymentSchedule schedule = schedule();
        LocalDate storedDate = LocalDate.of(2026, 8, 1);
        RepaymentInstallmentProgress first = partial(schedule, storedDate);
        RepaymentInstallmentProgress second = RepaymentInstallmentProgress.initial(
                schedule, schedule.items().get(1), storedDate, storedDate.atStartOfDay());
        RepaymentBalance balance = new RepaymentBalance(
                money("0"), money("100"), money("10"), money("110"),
                money("2000"), money("100"), money("10"), money("2110"),
                LocalDate.of(2026, 8, 1), at("2026-08-01T10:00:00"), storedDate);
        LoanAccount account = account(schedule, LoanAccountStatus.ACTIVE, balance);

        OverdueServicingCalculator.Result onDue = evaluate(account, schedule, List.of(first, second),
                LocalDate.of(2026, 8, 27));
        assertEquals(RepaymentInstallmentStatus.PARTIALLY_PAID,
                onDue.progress().getFirst().status());

        OverdueServicingCalculator.Result overdue = evaluate(
                account.withServicingState(onDue.balance(), onDue.accountStatus(), at("2026-08-27T00:00:00")),
                schedule, onDue.progress(), LocalDate.of(2026, 8, 28));
        assertEquals(RepaymentInstallmentStatus.OVERDUE,
                overdue.progress().getFirst().status());
        assertEquals(LoanAccountStatus.OVERDUE, overdue.accountStatus());
    }

    @Test
    void paidInstallmentRemainsPaidAndAuthoritativeProgressCanCureOverdueAccount() {
        RepaymentSchedule schedule = schedule();
        LocalDate storedDate = LocalDate.of(2026, 8, 28);
        RepaymentInstallmentProgress paid = paid(schedule, storedDate);
        RepaymentInstallmentProgress future = RepaymentInstallmentProgress.initial(
                schedule, schedule.items().get(1), storedDate, storedDate.atStartOfDay());
        RepaymentBalance balance = new RepaymentBalance(
                money("1000"), money("100"), money("10"), money("1110"),
                money("1000"), money("100"), money("10"), money("1110"),
                LocalDate.of(2026, 8, 28), at("2026-08-28T09:00:00"), storedDate);

        OverdueServicingCalculator.Result result = evaluate(
                account(schedule, LoanAccountStatus.OVERDUE, balance), schedule,
                List.of(paid, future), LocalDate.of(2026, 8, 29));

        assertEquals(RepaymentInstallmentStatus.PAID, result.progress().getFirst().status());
        assertEquals(RepaymentInstallmentStatus.NOT_DUE, result.progress().getLast().status());
        assertEquals(LoanAccountStatus.ACTIVE, result.accountStatus());
    }

    @Test
    void rejectsBackwardEvaluationAndNeverChangesFinancialOrPaymentEvidence() {
        RepaymentSchedule schedule = schedule();
        LocalDate storedDate = LocalDate.of(2026, 8, 20);
        List<RepaymentInstallmentProgress> progress = initial(schedule, storedDate);
        RepaymentBalance balance = RepaymentBalance.initial(
                money("2000"), money("200"), money("20"), storedDate);
        LoanAccount account = account(schedule, LoanAccountStatus.ACTIVE, balance);

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> evaluate(account, schedule, progress, storedDate.minusDays(1)));
        assertEquals("SYSTEM_STATE_CONFLICT", exception.getErrorCode());

        OverdueServicingCalculator.Result result = evaluate(
                account, schedule, progress, storedDate.plusDays(1));
        assertEquals(balance.principalPaid(), result.balance().principalPaid());
        assertEquals(balance.totalOutstanding(), result.balance().totalOutstanding());
        assertEquals(balance.lastPaymentValueDate(), result.balance().lastPaymentValueDate());
        assertEquals(balance.lastPaymentRecordedAt(), result.balance().lastPaymentRecordedAt());
    }

    private OverdueServicingCalculator.Result evaluate(
            LoanAccount account,
            RepaymentSchedule schedule,
            List<RepaymentInstallmentProgress> progress,
            LocalDate date
    ) {
        return calculator.evaluate(account, schedule, progress, date, date.atStartOfDay());
    }

    private static List<RepaymentInstallmentStatus> statuses(OverdueServicingCalculator.Result result) {
        return result.progress().stream().map(RepaymentInstallmentProgress::status).toList();
    }

    private static List<RepaymentInstallmentProgress> initial(
            RepaymentSchedule schedule, LocalDate date
    ) {
        return schedule.items().stream().map(item -> RepaymentInstallmentProgress.initial(
                schedule, item, date, date.atStartOfDay())).toList();
    }

    private static RepaymentInstallmentProgress partial(RepaymentSchedule schedule, LocalDate date) {
        RepaymentScheduleItem item = schedule.items().getFirst();
        return new RepaymentInstallmentProgress(
                item.id(), schedule.id(), schedule.loanAccountId(), 1,
                money("0"), money("100"), money("10"), money("110"),
                money("1000"), money("0"), money("0"), money("1000"),
                RepaymentInstallmentStatus.PARTIALLY_PAID,
                date, at("2026-08-01T10:00:00"), date, date.atStartOfDay());
    }

    private static RepaymentInstallmentProgress paid(RepaymentSchedule schedule, LocalDate date) {
        RepaymentScheduleItem item = schedule.items().getFirst();
        return new RepaymentInstallmentProgress(
                item.id(), schedule.id(), schedule.loanAccountId(), 1,
                money("1000"), money("100"), money("10"), money("1110"),
                money("0"), money("0"), money("0"), money("0"),
                RepaymentInstallmentStatus.PAID,
                date, at("2026-08-28T09:00:00"), date, date.atStartOfDay());
    }

    private static LoanAccount account(
            RepaymentSchedule schedule, LoanAccountStatus status, RepaymentBalance balance
    ) {
        LocalDateTime activatedAt = at("2026-07-01T10:00:00");
        return new LoanAccount(
                schedule.loanAccountId(), schedule.loanApplicationId(), schedule.loanContractId(),
                UUID.randomUUID(), LoanAccount.accountNumberFor(schedule.loanAccountId()), status,
                money("2000"), 2, money("200"), money("20"), money("2220"),
                activatedAt, balance, activatedAt.isAfter(balance.servicingEvaluationDate().atStartOfDay())
                ? activatedAt : balance.servicingEvaluationDate().atStartOfDay());
    }

    private static RepaymentSchedule schedule() {
        LocalDate first = LocalDate.of(2026, 8, 27);
        LocalDate second = LocalDate.of(2026, 9, 27);
        UUID accountId = UUID.randomUUID();
        return new RepaymentSchedule(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), accountId,
                RepaymentScheduleType.FINAL, 1, 2, money("2000"), money("200"), money("20"),
                money("2220"), first, second, at("2026-07-01T10:00:00"),
                List.of(item(1, first), item(2, second)));
    }

    private static RepaymentScheduleItem item(int number, LocalDate date) {
        return new RepaymentScheduleItem(
                UUID.randomUUID(), UUID.randomUUID(), number, date,
                money("1000"), money("100"), money("10"), money("1110"));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private static LocalDateTime at(String value) {
        return LocalDateTime.parse(value);
    }
}
