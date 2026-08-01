package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record RepaymentInstallmentProgress(
        UUID repaymentScheduleItemId,
        UUID repaymentScheduleId,
        UUID loanAccountId,
        int installmentNumber,
        BigDecimal principalPaid,
        BigDecimal interestPaid,
        BigDecimal feePaid,
        BigDecimal totalPaid,
        BigDecimal principalOutstanding,
        BigDecimal interestOutstanding,
        BigDecimal feeOutstanding,
        BigDecimal totalOutstanding,
        RepaymentInstallmentStatus status,
        LocalDate lastPaymentValueDate,
        LocalDateTime lastPaymentRecordedAt,
        LocalDate servicingEvaluationDate,
        LocalDateTime updatedAt
) {

    public RepaymentInstallmentProgress {
        Objects.requireNonNull(repaymentScheduleItemId,
                "repaymentScheduleItemId must not be null");
        Objects.requireNonNull(repaymentScheduleId,
                "repaymentScheduleId must not be null");
        Objects.requireNonNull(loanAccountId, "loanAccountId must not be null");
        if (installmentNumber <= 0) {
            throw invalid("Installment number must be positive.");
        }
        requireWholeVnd(principalPaid, "principalPaid");
        requireWholeVnd(interestPaid, "interestPaid");
        requireWholeVnd(feePaid, "feePaid");
        requireWholeVnd(totalPaid, "totalPaid");
        requireWholeVnd(principalOutstanding, "principalOutstanding");
        requireWholeVnd(interestOutstanding, "interestOutstanding");
        requireWholeVnd(feeOutstanding, "feeOutstanding");
        requireWholeVnd(totalOutstanding, "totalOutstanding");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(servicingEvaluationDate,
                "servicingEvaluationDate must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        if (totalPaid.compareTo(principalPaid.add(interestPaid).add(feePaid)) != 0
                || totalOutstanding.compareTo(
                        principalOutstanding.add(interestOutstanding).add(feeOutstanding)
                ) != 0) {
            throw invalid("Installment progress totals do not reconcile.");
        }
        boolean hasPayment = totalPaid.signum() > 0;
        if (hasPayment != (lastPaymentValueDate != null)
                || hasPayment != (lastPaymentRecordedAt != null)) {
            throw invalid("Installment payment dates do not match paid evidence.");
        }
        if (status == RepaymentInstallmentStatus.PAID && totalOutstanding.signum() != 0) {
            throw invalid("Paid installment must have zero outstanding.");
        }
        if (status != RepaymentInstallmentStatus.PAID && totalOutstanding.signum() == 0) {
            throw invalid("Zero-outstanding installment must be paid.");
        }
    }

    public static RepaymentInstallmentProgress initial(
            RepaymentSchedule schedule,
            RepaymentScheduleItem item,
            LocalDate evaluationDate,
            LocalDateTime updatedAt
    ) {
        Objects.requireNonNull(schedule, "schedule must not be null");
        Objects.requireNonNull(item, "item must not be null");
        if (!schedule.items().contains(item)) {
            throw invalid("Schedule item does not belong to the repayment schedule.");
        }
        RepaymentInstallmentStatus initialStatus = item.dueDate().isBefore(evaluationDate)
                ? RepaymentInstallmentStatus.OVERDUE
                : item.dueDate().equals(evaluationDate)
                        ? RepaymentInstallmentStatus.DUE
                        : RepaymentInstallmentStatus.NOT_DUE;
        return new RepaymentInstallmentProgress(
                item.id(),
                schedule.id(),
                schedule.loanAccountId(),
                item.installmentNumber(),
                zero(),
                zero(),
                zero(),
                zero(),
                item.principalDue(),
                item.interestDue(),
                item.feeDue(),
                item.totalDue(),
                initialStatus,
                null,
                null,
                evaluationDate,
                updatedAt
        );
    }

    public void validateAgainst(RepaymentScheduleItem scheduleItem) {
        Objects.requireNonNull(scheduleItem, "scheduleItem must not be null");
        if (!repaymentScheduleItemId.equals(scheduleItem.id())
                || installmentNumber != scheduleItem.installmentNumber()
                || principalPaid.add(principalOutstanding).compareTo(
                        scheduleItem.principalDue()) != 0
                || interestPaid.add(interestOutstanding).compareTo(
                        scheduleItem.interestDue()) != 0
                || feePaid.add(feeOutstanding).compareTo(scheduleItem.feeDue()) != 0
                || totalPaid.add(totalOutstanding).compareTo(scheduleItem.totalDue()) != 0) {
            throw invalid("Installment progress does not reconcile to its schedule item.");
        }
    }

    private static void requireWholeVnd(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.signum() < 0 || value.remainder(BigDecimal.ONE).signum() != 0) {
            throw invalid(fieldName + " must be a non-negative whole VND amount.");
        }
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    private static BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException(
                "REPAYMENT_INSTALLMENT_PROGRESS_INVALID",
                message
        );
    }
}
