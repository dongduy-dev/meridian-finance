package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.RepaymentBalance;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleItem;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class OverdueServicingCalculator {

    private final RepaymentStatusCalculator statuses = new RepaymentStatusCalculator();

    public Result evaluate(
            LoanAccount account,
            RepaymentSchedule schedule,
            List<RepaymentInstallmentProgress> currentProgress,
            LocalDate evaluationDate,
            LocalDateTime evaluatedAt
    ) {
        Objects.requireNonNull(account, "account must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");
        Objects.requireNonNull(evaluationDate, "evaluationDate must not be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        if (evaluationDate.isBefore(account.servicingEvaluationDate())) {
            throw conflict();
        }
        if (account.status() != LoanAccountStatus.ACTIVE
                && account.status() != LoanAccountStatus.OVERDUE) {
            throw conflict();
        }
        if (account.repaymentBalance().totalOutstanding().signum() <= 0) {
            throw conflict();
        }
        if (!account.id().equals(schedule.loanAccountId())
                || !account.loanApplicationId().equals(schedule.loanApplicationId())) {
            throw conflict();
        }

        Map<UUID, RepaymentInstallmentProgress> progressByItem = new LinkedHashMap<>();
        for (RepaymentInstallmentProgress progress : List.copyOf(currentProgress)) {
            if (progressByItem.put(progress.repaymentScheduleItemId(), progress) != null
                    || !account.id().equals(progress.loanAccountId())
                    || !schedule.id().equals(progress.repaymentScheduleId())
                    || evaluationDate.isBefore(progress.servicingEvaluationDate())) {
                throw conflict();
            }
        }
        if (progressByItem.size() != schedule.items().size()) {
            throw conflict();
        }

        List<RepaymentInstallmentProgress> updated = schedule.items().stream()
                .map(item -> evaluateInstallment(
                        account, schedule, item, progressByItem.remove(item.id()),
                        evaluationDate, evaluatedAt
                ))
                .toList();
        if (!progressByItem.isEmpty()) {
            throw conflict();
        }
        validateAccountRollup(account, updated);

        RepaymentBalance previous = account.repaymentBalance();
        RepaymentBalance balance = new RepaymentBalance(
                previous.principalPaid(), previous.interestPaid(), previous.feePaid(),
                previous.totalPaid(), previous.principalOutstanding(),
                previous.interestOutstanding(), previous.feeOutstanding(),
                previous.totalOutstanding(), previous.lastPaymentValueDate(),
                previous.lastPaymentRecordedAt(), evaluationDate
        );
        LoanAccountStatus status = statuses.loanAccountStatus(
                balance.totalOutstanding(), updated
        );
        if (status == LoanAccountStatus.SETTLED || status == LoanAccountStatus.CLOSED) {
            throw conflict();
        }
        List<UUID> changedItems = updated.stream()
                .filter(item -> item.status() != currentProgress.stream()
                        .filter(before -> before.repaymentScheduleItemId().equals(
                                item.repaymentScheduleItemId()
                        ))
                        .findFirst()
                        .orElseThrow(OverdueServicingCalculator::conflict)
                        .status())
                .map(RepaymentInstallmentProgress::repaymentScheduleItemId)
                .toList();
        return new Result(updated, balance, status, changedItems);
    }

    private RepaymentInstallmentProgress evaluateInstallment(
            LoanAccount account,
            RepaymentSchedule schedule,
            RepaymentScheduleItem item,
            RepaymentInstallmentProgress current,
            LocalDate evaluationDate,
            LocalDateTime evaluatedAt
    ) {
        if (current == null
                || !account.id().equals(current.loanAccountId())
                || current.installmentNumber() != item.installmentNumber()) {
            throw conflict();
        }
        current.validateAgainst(item);
        RepaymentInstallmentStatus status = statuses.installmentStatus(
                item.dueDate(), current.totalPaid(), current.totalOutstanding(), evaluationDate
        );
        return new RepaymentInstallmentProgress(
                current.repaymentScheduleItemId(), schedule.id(), account.id(),
                current.installmentNumber(), current.principalPaid(), current.interestPaid(),
                current.feePaid(), current.totalPaid(), current.principalOutstanding(),
                current.interestOutstanding(), current.feeOutstanding(),
                current.totalOutstanding(), status, current.lastPaymentValueDate(),
                current.lastPaymentRecordedAt(), evaluationDate, evaluatedAt
        );
    }

    private static void validateAccountRollup(
            LoanAccount account,
            List<RepaymentInstallmentProgress> progress
    ) {
        RepaymentBalance balance = account.repaymentBalance();
        if (sum(progress, RepaymentInstallmentProgress::principalPaid)
                .compareTo(balance.principalPaid()) != 0
                || sum(progress, RepaymentInstallmentProgress::interestPaid)
                .compareTo(balance.interestPaid()) != 0
                || sum(progress, RepaymentInstallmentProgress::feePaid)
                .compareTo(balance.feePaid()) != 0
                || sum(progress, RepaymentInstallmentProgress::totalPaid)
                .compareTo(balance.totalPaid()) != 0
                || sum(progress, RepaymentInstallmentProgress::principalOutstanding)
                .compareTo(balance.principalOutstanding()) != 0
                || sum(progress, RepaymentInstallmentProgress::interestOutstanding)
                .compareTo(balance.interestOutstanding()) != 0
                || sum(progress, RepaymentInstallmentProgress::feeOutstanding)
                .compareTo(balance.feeOutstanding()) != 0
                || sum(progress, RepaymentInstallmentProgress::totalOutstanding)
                .compareTo(balance.totalOutstanding()) != 0) {
            throw conflict();
        }
    }

    private static BigDecimal sum(
            List<RepaymentInstallmentProgress> progress,
            java.util.function.Function<RepaymentInstallmentProgress, BigDecimal> mapper
    ) {
        return progress.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BusinessStateConflictException conflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Loan Account overdue servicing evidence is inconsistent."
        );
    }

    public record Result(
            List<RepaymentInstallmentProgress> progress,
            RepaymentBalance balance,
            LoanAccountStatus accountStatus,
            List<UUID> installmentStatusChanges
    ) {
        public Result {
            progress = List.copyOf(progress);
            Objects.requireNonNull(balance);
            Objects.requireNonNull(accountStatus);
            installmentStatusChanges = List.copyOf(installmentStatusChanges);
        }
    }
}
