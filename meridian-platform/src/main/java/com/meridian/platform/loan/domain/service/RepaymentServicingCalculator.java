package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.RepaymentAllocation;
import com.meridian.platform.loan.domain.model.RepaymentAllocationComponent;
import com.meridian.platform.loan.domain.model.RepaymentBalance;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleItem;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RepaymentServicingCalculator {

    private final RepaymentStatusCalculator statuses = new RepaymentStatusCalculator();

    public Result apply(
            RepaymentSchedule schedule,
            List<RepaymentInstallmentProgress> currentProgress,
            List<RepaymentAllocation> allocations,
            LocalDate paymentValueDate,
            LocalDateTime recordedAt,
            LocalDate evaluationDate
    ) {
        Objects.requireNonNull(schedule);
        Objects.requireNonNull(paymentValueDate);
        Objects.requireNonNull(recordedAt);
        Objects.requireNonNull(evaluationDate);
        Map<UUID, RepaymentInstallmentProgress> progressByItem = new LinkedHashMap<>();
        for (RepaymentInstallmentProgress progress : currentProgress) {
            if (progressByItem.put(progress.repaymentScheduleItemId(), progress) != null
                    || evaluationDate.isBefore(progress.servicingEvaluationDate())) {
                throw invalid();
            }
        }
        Map<UUID, EnumMap<RepaymentAllocationComponent, BigDecimal>> allocated =
                allocationTotals(allocations);

        List<RepaymentInstallmentProgress> updated = schedule.items().stream()
                .map(item -> updateInstallment(
                        schedule,
                        item,
                        progressByItem.get(item.id()),
                        allocated.getOrDefault(item.id(), emptyComponents()),
                        paymentValueDate,
                        recordedAt,
                        evaluationDate
                ))
                .toList();
        if (updated.size() != progressByItem.size()) {
            throw invalid();
        }

        RepaymentBalance balance = new RepaymentBalance(
                sum(updated, RepaymentInstallmentProgress::principalPaid),
                sum(updated, RepaymentInstallmentProgress::interestPaid),
                sum(updated, RepaymentInstallmentProgress::feePaid),
                sum(updated, RepaymentInstallmentProgress::totalPaid),
                sum(updated, RepaymentInstallmentProgress::principalOutstanding),
                sum(updated, RepaymentInstallmentProgress::interestOutstanding),
                sum(updated, RepaymentInstallmentProgress::feeOutstanding),
                sum(updated, RepaymentInstallmentProgress::totalOutstanding),
                updated.stream().map(RepaymentInstallmentProgress::lastPaymentValueDate)
                        .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null),
                updated.stream().map(RepaymentInstallmentProgress::lastPaymentRecordedAt)
                        .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null),
                evaluationDate
        );
        LoanAccountStatus accountStatus = statuses.loanAccountStatus(
                balance.totalOutstanding(),
                updated
        );
        List<UUID> changedStatuses = updated.stream()
                .filter(item -> item.status() != progressByItem
                        .get(item.repaymentScheduleItemId()).status())
                .map(RepaymentInstallmentProgress::repaymentScheduleItemId)
                .toList();
        return new Result(updated, balance, accountStatus, changedStatuses);
    }

    private RepaymentInstallmentProgress updateInstallment(
            RepaymentSchedule schedule,
            RepaymentScheduleItem item,
            RepaymentInstallmentProgress current,
            Map<RepaymentAllocationComponent, BigDecimal> allocated,
            LocalDate paymentValueDate,
            LocalDateTime recordedAt,
            LocalDate evaluationDate
    ) {
        if (current == null
                || !current.repaymentScheduleId().equals(schedule.id())
                || !current.loanAccountId().equals(schedule.loanAccountId())) {
            throw invalid();
        }
        current.validateAgainst(item);
        BigDecimal principal = allocated.get(RepaymentAllocationComponent.PRINCIPAL);
        BigDecimal interest = allocated.get(RepaymentAllocationComponent.INTEREST);
        BigDecimal fee = allocated.get(RepaymentAllocationComponent.FEE);
        if (principal.compareTo(current.principalOutstanding()) > 0
                || interest.compareTo(current.interestOutstanding()) > 0
                || fee.compareTo(current.feeOutstanding()) > 0) {
            throw invalid();
        }
        BigDecimal principalPaid = current.principalPaid().add(principal);
        BigDecimal interestPaid = current.interestPaid().add(interest);
        BigDecimal feePaid = current.feePaid().add(fee);
        BigDecimal totalPaid = principalPaid.add(interestPaid).add(feePaid);
        BigDecimal principalOutstanding = current.principalOutstanding().subtract(principal);
        BigDecimal interestOutstanding = current.interestOutstanding().subtract(interest);
        BigDecimal feeOutstanding = current.feeOutstanding().subtract(fee);
        BigDecimal totalOutstanding = principalOutstanding
                .add(interestOutstanding).add(feeOutstanding);
        boolean receivedAllocation = principal.add(interest).add(fee).signum() > 0;
        LocalDate lastValueDate = receivedAllocation
                ? maximum(current.lastPaymentValueDate(), paymentValueDate)
                : current.lastPaymentValueDate();
        LocalDateTime lastRecordedAt = receivedAllocation
                ? maximum(current.lastPaymentRecordedAt(), recordedAt)
                : current.lastPaymentRecordedAt();
        RepaymentInstallmentStatus status = statuses.installmentStatus(
                item.dueDate(), totalPaid, totalOutstanding, evaluationDate
        );
        return new RepaymentInstallmentProgress(
                item.id(), schedule.id(), schedule.loanAccountId(),
                item.installmentNumber(), principalPaid, interestPaid, feePaid,
                totalPaid, principalOutstanding, interestOutstanding, feeOutstanding,
                totalOutstanding, status, lastValueDate, lastRecordedAt,
                evaluationDate, recordedAt
        );
    }

    private static Map<UUID, EnumMap<RepaymentAllocationComponent, BigDecimal>>
    allocationTotals(List<RepaymentAllocation> allocations) {
        Map<UUID, EnumMap<RepaymentAllocationComponent, BigDecimal>> totals =
                new LinkedHashMap<>();
        for (RepaymentAllocation allocation : allocations) {
            EnumMap<RepaymentAllocationComponent, BigDecimal> components = totals
                    .computeIfAbsent(
                            allocation.repaymentScheduleItemId(),
                            ignored -> emptyComponents()
                    );
            components.merge(allocation.component(), allocation.amount(), BigDecimal::add);
        }
        return totals;
    }

    private static EnumMap<RepaymentAllocationComponent, BigDecimal> emptyComponents() {
        EnumMap<RepaymentAllocationComponent, BigDecimal> result =
                new EnumMap<>(RepaymentAllocationComponent.class);
        for (RepaymentAllocationComponent component : RepaymentAllocationComponent.values()) {
            result.put(component, BigDecimal.ZERO.setScale(2));
        }
        return result;
    }

    private static BigDecimal sum(
            List<RepaymentInstallmentProgress> progress,
            java.util.function.Function<RepaymentInstallmentProgress, BigDecimal> mapper
    ) {
        return progress.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LocalDate maximum(LocalDate first, LocalDate second) {
        return first == null || second.isAfter(first) ? second : first;
    }

    private static LocalDateTime maximum(LocalDateTime first, LocalDateTime second) {
        return first == null || second.isAfter(first) ? second : first;
    }

    private static BusinessRuleViolationException invalid() {
        return new BusinessRuleViolationException(
                "REPAYMENT_SERVICING_INVALID",
                "Repayment servicing evidence is incomplete or inconsistent."
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
