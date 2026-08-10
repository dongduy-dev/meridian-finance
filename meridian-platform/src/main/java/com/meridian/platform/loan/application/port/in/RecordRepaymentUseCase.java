package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.RepaymentAllocationComponent;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import com.meridian.platform.loan.domain.model.RepaymentTransaction;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface RecordRepaymentUseCase {

    Result record(Command command);

    record Command(
            UUID requestId,
            UUID loanApplicationId,
            String externalPaymentReference,
            BigDecimal amount,
            LocalDate paymentValueDate
    ) {
        public Command {
            if (requestId == null || loanApplicationId == null) {
                throw invalid("Repayment command identifiers are required.");
            }
            externalPaymentReference = RepaymentTransaction.canonicalizeReference(
                    externalPaymentReference
            );
            if (amount == null || amount.signum() <= 0
                    || amount.remainder(BigDecimal.ONE).signum() != 0) {
                throw new BusinessRuleViolationException(
                        "REPAYMENT_AMOUNT_INVALID",
                        "Repayment amount must be a positive whole VND amount."
                );
            }
            if (paymentValueDate == null) {
                throw new BusinessRuleViolationException(
                        "REPAYMENT_VALUE_DATE_INVALID",
                        "Payment value date is required."
                );
            }
        }

        @Override
        public String toString() {
            return "Command[loanApplicationId=" + loanApplicationId
                    + ", amount=" + amount
                    + ", paymentValueDate=" + paymentValueDate
                    + ", operationEvidence=redacted]";
        }

        private static BusinessRuleViolationException invalid(String message) {
            return new BusinessRuleViolationException(
                    "REPAYMENT_COMMAND_INVALID",
                    message
            );
        }
    }

    record Result(
            UUID loanApplicationId,
            UUID loanAccountId,
            UUID repaymentTransactionId,
            UUID repaymentScheduleId,
            BigDecimal receivedAmount,
            LocalDate paymentValueDate,
            LocalDateTime recordedAt,
            List<Allocation> allocations,
            List<InstallmentProgress> installmentProgress,
            AccountBalance accountBalance,
            BigDecimal principalAllocatedAndReleased,
            boolean idempotentReplay
    ) {
        public Result {
            Objects.requireNonNull(loanApplicationId);
            Objects.requireNonNull(loanAccountId);
            Objects.requireNonNull(repaymentTransactionId);
            Objects.requireNonNull(repaymentScheduleId);
            Objects.requireNonNull(receivedAmount);
            Objects.requireNonNull(paymentValueDate);
            Objects.requireNonNull(recordedAt);
            allocations = List.copyOf(allocations);
            installmentProgress = List.copyOf(installmentProgress);
            Objects.requireNonNull(accountBalance);
            Objects.requireNonNull(principalAllocatedAndReleased);
        }

        @Override
        public String toString() {
            return "Result[loanApplicationId=" + loanApplicationId
                    + ", loanAccountId=" + loanAccountId
                    + ", repaymentTransactionId=" + repaymentTransactionId
                    + ", repaymentScheduleId=" + repaymentScheduleId
                    + ", idempotentReplay=" + idempotentReplay
                    + ", financialEvidence=redacted]";
        }
    }

    record Allocation(
            int sequence,
            UUID repaymentScheduleItemId,
            int installmentNumber,
            RepaymentAllocationComponent component,
            BigDecimal amount
    ) {
    }

    record InstallmentProgress(
            UUID repaymentScheduleItemId,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            BigDecimal feePaid,
            BigDecimal totalPaid,
            BigDecimal principalOutstanding,
            BigDecimal interestOutstanding,
            BigDecimal feeOutstanding,
            BigDecimal totalOutstanding,
            RepaymentInstallmentStatus previousStatus,
            RepaymentInstallmentStatus status,
            LocalDate lastPaymentValueDate,
            LocalDateTime lastPaymentRecordedAt,
            LocalDate servicingEvaluationDate,
            boolean statusChanged
    ) {
        public InstallmentProgress {
            Objects.requireNonNull(dueDate, "dueDate must not be null");
        }
    }

    record AccountBalance(
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            BigDecimal feePaid,
            BigDecimal totalPaid,
            BigDecimal principalOutstanding,
            BigDecimal interestOutstanding,
            BigDecimal feeOutstanding,
            BigDecimal totalOutstanding,
            LocalDate lastPaymentValueDate,
            LocalDateTime lastPaymentRecordedAt,
            LocalDate servicingEvaluationDate,
            LoanAccountStatus status
    ) {
    }
}
