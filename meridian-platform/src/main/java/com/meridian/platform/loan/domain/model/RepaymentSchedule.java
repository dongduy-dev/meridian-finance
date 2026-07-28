package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RepaymentSchedule(
        UUID id,
        UUID loanApplicationId,
        UUID loanContractId,
        UUID loanAccountId,
        RepaymentScheduleType scheduleType,
        int version,
        int approvedTermMonths,
        BigDecimal approvedPrincipal,
        BigDecimal totalInterest,
        BigDecimal feeAmount,
        BigDecimal totalRepaymentAmount,
        LocalDate firstDueDate,
        LocalDate lastDueDate,
        LocalDateTime generatedAt,
        List<RepaymentScheduleItem> items
) {

    public static final int INITIAL_FINAL_VERSION = 1;

    public RepaymentSchedule {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(loanContractId, "loanContractId must not be null");
        Objects.requireNonNull(loanAccountId, "loanAccountId must not be null");
        Objects.requireNonNull(scheduleType, "scheduleType must not be null");
        Objects.requireNonNull(approvedPrincipal, "approvedPrincipal must not be null");
        Objects.requireNonNull(totalInterest, "totalInterest must not be null");
        Objects.requireNonNull(feeAmount, "feeAmount must not be null");
        Objects.requireNonNull(totalRepaymentAmount, "totalRepaymentAmount must not be null");
        Objects.requireNonNull(firstDueDate, "firstDueDate must not be null");
        Objects.requireNonNull(lastDueDate, "lastDueDate must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));

        if (scheduleType != RepaymentScheduleType.FINAL || version != INITIAL_FINAL_VERSION) {
            throw invalid("Only FINAL repayment schedule version 1 is supported.");
        }
        if (approvedTermMonths <= 0 || items.size() != approvedTermMonths) {
            throw invalid("Repayment schedule item count does not match the approved term.");
        }
        requirePositiveWholeVnd(approvedPrincipal, "approvedPrincipal");
        requireNonNegativeWholeVnd(totalInterest, "totalInterest");
        requireNonNegativeWholeVnd(feeAmount, "feeAmount");
        requirePositiveWholeVnd(totalRepaymentAmount, "totalRepaymentAmount");
        if (totalRepaymentAmount.compareTo(approvedPrincipal.add(totalInterest).add(feeAmount)) != 0) {
            throw invalid("Repayment schedule header amounts do not reconcile.");
        }
        validateItems(
                items,
                firstDueDate,
                lastDueDate,
                approvedPrincipal,
                totalInterest,
                feeAmount,
                totalRepaymentAmount
        );
    }

    @Override
    public String toString() {
        return "RepaymentSchedule[id=" + id + ", loanApplicationId=" + loanApplicationId
                + ", loanContractId=" + loanContractId + ", loanAccountId=" + loanAccountId
                + ", scheduleType=" + scheduleType + ", version=" + version
                + ", financialEvidence=redacted]";
    }

    private static void validateItems(
            List<RepaymentScheduleItem> items,
            LocalDate firstDueDate,
            LocalDate lastDueDate,
            BigDecimal approvedPrincipal,
            BigDecimal totalInterest,
            BigDecimal feeAmount,
            BigDecimal totalRepaymentAmount
    ) {
        HashSet<UUID> sourceIds = new HashSet<>();
        LocalDate previousDueDate = null;
        BigDecimal principalSum = BigDecimal.ZERO;
        BigDecimal interestSum = BigDecimal.ZERO;
        BigDecimal feeSum = BigDecimal.ZERO;
        BigDecimal totalSum = BigDecimal.ZERO;

        for (int index = 0; index < items.size(); index++) {
            RepaymentScheduleItem item = items.get(index);
            if (item.installmentNumber() != index + 1) {
                throw invalid("Repayment schedule installment sequence is not continuous.");
            }
            if (!sourceIds.add(item.sourceLoanContractRepaymentItemId())) {
                throw invalid("Repayment schedule contains a duplicate contract item source.");
            }
            if (previousDueDate != null && !item.dueDate().isAfter(previousDueDate)) {
                throw invalid("Repayment schedule due dates must be strictly increasing.");
            }
            previousDueDate = item.dueDate();
            principalSum = principalSum.add(item.principalDue());
            interestSum = interestSum.add(item.interestDue());
            feeSum = feeSum.add(item.feeDue());
            totalSum = totalSum.add(item.totalDue());
        }

        if (!items.getFirst().dueDate().equals(firstDueDate)
                || !items.getLast().dueDate().equals(lastDueDate)) {
            throw invalid("Repayment schedule boundary dates do not match its items.");
        }
        if (principalSum.compareTo(approvedPrincipal) != 0
                || interestSum.compareTo(totalInterest) != 0
                || feeSum.compareTo(feeAmount) != 0
                || totalSum.compareTo(totalRepaymentAmount) != 0) {
            throw invalid("Repayment schedule items do not reconcile to the header.");
        }
    }

    private static void requirePositiveWholeVnd(BigDecimal value, String fieldName) {
        requireNonNegativeWholeVnd(value, fieldName);
        if (value.signum() <= 0) {
            throw invalid(fieldName + " must be positive.");
        }
    }

    private static void requireNonNegativeWholeVnd(BigDecimal value, String fieldName) {
        if (value.signum() < 0 || value.remainder(BigDecimal.ONE).signum() != 0) {
            throw invalid(fieldName + " must be a non-negative whole VND amount.");
        }
    }

    private static BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException("REPAYMENT_SCHEDULE_INVALID", message);
    }
}
