package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.RepaymentTransaction;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public interface ApproveLoanSettlementUseCase {

    Result approve(Command command);

    record Command(
            UUID requestId,
            UUID loanApplicationId,
            BigDecimal expectedSettlementAmount,
            LocalDate paymentValueDate,
            String externalPaymentReference
    ) {
        public Command {
            if (requestId == null || loanApplicationId == null) {
                throw invalidCommand("Settlement command identifiers are required.");
            }
            if (expectedSettlementAmount == null
                    || expectedSettlementAmount.signum() <= 0
                    || expectedSettlementAmount.remainder(BigDecimal.ONE).signum() != 0) {
                throw new BusinessRuleViolationException(
                        "SETTLEMENT_AMOUNT_INVALID",
                        "Expected settlement amount must be a positive whole VND amount."
                );
            }
            if (paymentValueDate == null) {
                throw new BusinessRuleViolationException(
                        "SETTLEMENT_VALUE_DATE_INVALID",
                        "Settlement payment value date is required."
                );
            }
            externalPaymentReference = RepaymentTransaction.canonicalizeReference(
                    externalPaymentReference
            );
        }

        @Override
        public String toString() {
            return "Command[loanApplicationId=" + loanApplicationId
                    + ", expectedSettlementAmount=redacted"
                    + ", paymentValueDate=" + paymentValueDate
                    + ", operationEvidence=redacted]";
        }

        private static BusinessRuleViolationException invalidCommand(String message) {
            return new BusinessRuleViolationException(
                    "SETTLEMENT_COMMAND_INVALID",
                    message
            );
        }
    }

    record Result(
            UUID loanApplicationId,
            UUID loanAccountId,
            UUID repaymentTransactionId,
            UUID repaymentScheduleId,
            BigDecimal settlementAmount,
            LocalDate paymentValueDate,
            LocalDateTime approvedAt,
            BigDecimal principalAllocated,
            BigDecimal principalReleased,
            AccountBalance accountBalance,
            boolean idempotentReplay
    ) {
        public Result {
            Objects.requireNonNull(loanApplicationId);
            Objects.requireNonNull(loanAccountId);
            Objects.requireNonNull(repaymentTransactionId);
            Objects.requireNonNull(repaymentScheduleId);
            Objects.requireNonNull(settlementAmount);
            Objects.requireNonNull(paymentValueDate);
            Objects.requireNonNull(approvedAt);
            Objects.requireNonNull(principalAllocated);
            Objects.requireNonNull(principalReleased);
            Objects.requireNonNull(accountBalance);
        }

        @Override
        public String toString() {
            return "Result[loanApplicationId=" + loanApplicationId
                    + ", loanAccountId=" + loanAccountId
                    + ", repaymentTransactionId=" + repaymentTransactionId
                    + ", idempotentReplay=" + idempotentReplay
                    + ", financialEvidence=redacted]";
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
