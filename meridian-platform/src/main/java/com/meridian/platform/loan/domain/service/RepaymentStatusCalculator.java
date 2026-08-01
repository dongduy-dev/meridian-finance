package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class RepaymentStatusCalculator {

    public RepaymentInstallmentStatus installmentStatus(
            LocalDate dueDate,
            BigDecimal paid,
            BigDecimal outstanding,
            LocalDate evaluationDate
    ) {
        Objects.requireNonNull(dueDate, "dueDate must not be null");
        requireNonNegative(paid, "paid");
        requireNonNegative(outstanding, "outstanding");
        Objects.requireNonNull(evaluationDate, "evaluationDate must not be null");
        if (outstanding.signum() == 0) {
            return RepaymentInstallmentStatus.PAID;
        }
        if (dueDate.isBefore(evaluationDate)) {
            return RepaymentInstallmentStatus.OVERDUE;
        }
        if (paid.signum() > 0) {
            return RepaymentInstallmentStatus.PARTIALLY_PAID;
        }
        if (dueDate.equals(evaluationDate)) {
            return RepaymentInstallmentStatus.DUE;
        }
        return RepaymentInstallmentStatus.NOT_DUE;
    }

    public LoanAccountStatus loanAccountStatus(
            BigDecimal totalOutstanding,
            List<RepaymentInstallmentProgress> installments
    ) {
        requireNonNegative(totalOutstanding, "totalOutstanding");
        List<RepaymentInstallmentProgress> immutableInstallments = List.copyOf(
                Objects.requireNonNull(installments, "installments must not be null")
        );
        if (totalOutstanding.signum() == 0) {
            return LoanAccountStatus.SETTLED;
        }
        if (immutableInstallments.stream().anyMatch(
                item -> item.status() == RepaymentInstallmentStatus.OVERDUE
        )) {
            return LoanAccountStatus.OVERDUE;
        }
        return LoanAccountStatus.ACTIVE;
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative.");
        }
    }
}
