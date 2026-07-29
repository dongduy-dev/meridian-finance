package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.RepaymentAllocation;
import com.meridian.platform.loan.domain.model.RepaymentAllocationComponent;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleItem;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DeterministicRepaymentAllocator {

    public List<RepaymentAllocation> allocate(
            UUID repaymentTransactionId,
            BigDecimal receivedAmount,
            RepaymentSchedule schedule,
            List<RepaymentInstallmentProgress> currentProgress
    ) {
        Objects.requireNonNull(repaymentTransactionId,
                "repaymentTransactionId must not be null");
        Objects.requireNonNull(receivedAmount, "receivedAmount must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");
        if (receivedAmount.signum() <= 0
                || receivedAmount.remainder(BigDecimal.ONE).signum() != 0) {
            throw invalid("Received amount must be a positive whole VND amount.");
        }

        Map<UUID, RepaymentInstallmentProgress> progressByItem = List.copyOf(
                        Objects.requireNonNull(currentProgress,
                                "currentProgress must not be null")
                ).stream()
                .collect(Collectors.toUnmodifiableMap(
                        RepaymentInstallmentProgress::repaymentScheduleItemId,
                        Function.identity()
                ));
        if (progressByItem.size() != schedule.items().size()) {
            throw invalid("Repayment progress is incomplete for the final schedule.");
        }
        BigDecimal totalOutstanding = progressByItem.values().stream()
                .map(RepaymentInstallmentProgress::totalOutstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (receivedAmount.compareTo(totalOutstanding) > 0) {
            throw invalid("Received amount exceeds total outstanding.");
        }

        List<RepaymentScheduleItem> orderedItems = schedule.items().stream()
                .sorted(Comparator.comparing(RepaymentScheduleItem::dueDate)
                        .thenComparingInt(RepaymentScheduleItem::installmentNumber))
                .toList();
        ArrayList<RepaymentAllocation> allocations = new ArrayList<>();
        BigDecimal remaining = receivedAmount;
        int sequence = 1;
        for (RepaymentScheduleItem item : orderedItems) {
            RepaymentInstallmentProgress progress = progressByItem.get(item.id());
            if (progress == null
                    || !progress.repaymentScheduleId().equals(schedule.id())
                    || !progress.loanAccountId().equals(schedule.loanAccountId())) {
                throw invalid("Repayment progress ownership conflicts with the schedule.");
            }
            progress.validateAgainst(item);
            sequence = allocateComponent(
                    repaymentTransactionId,
                    allocations,
                    sequence,
                    item.id(),
                    RepaymentAllocationComponent.FEE,
                    progress.feeOutstanding(),
                    remaining
            );
            remaining = remainingAfter(allocations, receivedAmount);
            sequence = allocateComponent(
                    repaymentTransactionId,
                    allocations,
                    sequence,
                    item.id(),
                    RepaymentAllocationComponent.INTEREST,
                    progress.interestOutstanding(),
                    remaining
            );
            remaining = remainingAfter(allocations, receivedAmount);
            sequence = allocateComponent(
                    repaymentTransactionId,
                    allocations,
                    sequence,
                    item.id(),
                    RepaymentAllocationComponent.PRINCIPAL,
                    progress.principalOutstanding(),
                    remaining
            );
            remaining = remainingAfter(allocations, receivedAmount);
            if (remaining.signum() == 0) {
                break;
            }
        }
        if (remaining.signum() != 0) {
            throw invalid("Repayment allocation could not reconcile the received amount.");
        }
        return List.copyOf(allocations);
    }

    private static int allocateComponent(
            UUID transactionId,
            List<RepaymentAllocation> allocations,
            int sequence,
            UUID scheduleItemId,
            RepaymentAllocationComponent component,
            BigDecimal outstanding,
            BigDecimal remaining
    ) {
        if (remaining.signum() == 0 || outstanding.signum() == 0) {
            return sequence;
        }
        BigDecimal allocated = remaining.min(outstanding);
        allocations.add(new RepaymentAllocation(
                allocationId(transactionId, sequence),
                transactionId,
                sequence,
                scheduleItemId,
                component,
                allocated
        ));
        return sequence + 1;
    }

    private static BigDecimal remainingAfter(
            List<RepaymentAllocation> allocations,
            BigDecimal receivedAmount
    ) {
        return receivedAmount.subtract(allocations.stream()
                .map(RepaymentAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static UUID allocationId(UUID transactionId, int sequence) {
        return UUID.nameUUIDFromBytes(
                (transactionId + ":allocation:" + sequence)
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    private static BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException("REPAYMENT_ALLOCATION_INVALID", message);
    }
}
