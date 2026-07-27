package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record RepaymentScheduleItem(
        UUID id,
        UUID sourceLoanContractRepaymentItemId,
        int installmentNumber,
        LocalDate dueDate,
        BigDecimal principalDue,
        BigDecimal interestDue,
        BigDecimal feeDue,
        BigDecimal totalDue
) {

    public RepaymentScheduleItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(
                sourceLoanContractRepaymentItemId,
                "sourceLoanContractRepaymentItemId must not be null"
        );
        Objects.requireNonNull(dueDate, "dueDate must not be null");
        Objects.requireNonNull(principalDue, "principalDue must not be null");
        Objects.requireNonNull(interestDue, "interestDue must not be null");
        Objects.requireNonNull(feeDue, "feeDue must not be null");
        Objects.requireNonNull(totalDue, "totalDue must not be null");

        if (installmentNumber <= 0) {
            throw invalid("Installment number must be positive.");
        }
        requireNonNegativeWholeVnd(principalDue, "principalDue");
        requireNonNegativeWholeVnd(interestDue, "interestDue");
        requireNonNegativeWholeVnd(feeDue, "feeDue");
        requireNonNegativeWholeVnd(totalDue, "totalDue");
        if (totalDue.compareTo(principalDue.add(interestDue).add(feeDue)) != 0) {
            throw invalid("Repayment schedule item amounts do not reconcile.");
        }
    }

    @Override
    public String toString() {
        return "RepaymentScheduleItem[id=" + id + ", installmentNumber=" + installmentNumber
                + ", dueDate=" + dueDate + ", financialEvidence=redacted]";
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
