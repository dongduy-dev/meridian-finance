package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record RepaymentAllocation(
        UUID id,
        UUID repaymentTransactionId,
        int allocationSequence,
        UUID repaymentScheduleItemId,
        RepaymentAllocationComponent component,
        BigDecimal amount
) {

    public RepaymentAllocation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(repaymentTransactionId,
                "repaymentTransactionId must not be null");
        Objects.requireNonNull(repaymentScheduleItemId,
                "repaymentScheduleItemId must not be null");
        Objects.requireNonNull(component, "component must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (allocationSequence <= 0) {
            throw invalid("Allocation sequence must be positive.");
        }
        if (amount.signum() <= 0 || amount.remainder(BigDecimal.ONE).signum() != 0) {
            throw invalid("Allocation amount must be a positive whole VND amount.");
        }
    }

    private static BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException("REPAYMENT_ALLOCATION_INVALID", message);
    }
}
