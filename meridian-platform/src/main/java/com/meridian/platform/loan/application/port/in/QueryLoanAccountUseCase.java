package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface QueryLoanAccountUseCase {

    Result query(UUID loanApplicationId);

    record Result(
            UUID loanApplicationId,
            UUID loanAccountId,
            String accountNumber,
            LoanAccountStatus status,
            LocalDateTime activatedAt,
            BigDecimal originatedPrincipal,
            int approvedTermMonths,
            BigDecimal totalInterest,
            BigDecimal totalFee,
            BigDecimal totalRepayment,
            ServicingSummary servicing,
            DestinationSummary destination,
            UUID repaymentScheduleId,
            RepaymentScheduleType scheduleType,
            int scheduleVersion,
            LocalDate firstDueDate,
            LocalDate lastDueDate,
            List<ScheduleItem> scheduleItems
    ) {
        public Result {
            Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
            Objects.requireNonNull(loanAccountId, "loanAccountId must not be null");
            accountNumber = requireText(accountNumber, "accountNumber");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(activatedAt, "activatedAt must not be null");
            Objects.requireNonNull(originatedPrincipal, "originatedPrincipal must not be null");
            Objects.requireNonNull(totalInterest, "totalInterest must not be null");
            Objects.requireNonNull(totalFee, "totalFee must not be null");
            Objects.requireNonNull(totalRepayment, "totalRepayment must not be null");
            Objects.requireNonNull(servicing, "servicing must not be null");
            Objects.requireNonNull(destination, "destination must not be null");
            Objects.requireNonNull(repaymentScheduleId, "repaymentScheduleId must not be null");
            Objects.requireNonNull(scheduleType, "scheduleType must not be null");
            Objects.requireNonNull(firstDueDate, "firstDueDate must not be null");
            Objects.requireNonNull(lastDueDate, "lastDueDate must not be null");
            scheduleItems = List.copyOf(Objects.requireNonNull(scheduleItems,
                    "scheduleItems must not be null"));
            if (approvedTermMonths <= 0 || scheduleVersion <= 0) {
                throw new IllegalArgumentException("Loan Account result versions and term must be positive.");
            }
        }

        @Override
        public String toString() {
            return "Result[loanApplicationId=" + loanApplicationId
                    + ", loanAccountId=" + loanAccountId
                    + ", status=" + status
                    + ", destinationAndFinancialEvidence=redacted]";
        }
    }

    record DestinationSummary(
            String bankCode,
            String bankName,
            String accountHolderName,
            String maskedAccountNumber
    ) {
        public DestinationSummary {
            bankCode = requireText(bankCode, "bankCode");
            bankName = requireText(bankName, "bankName");
            accountHolderName = requireText(accountHolderName, "accountHolderName");
            maskedAccountNumber = requireText(maskedAccountNumber, "maskedAccountNumber");
        }

        @Override
        public String toString() {
            return "DestinationSummary[destination=redacted]";
        }
    }

    record ScheduleItem(
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal principalDue,
            BigDecimal interestDue,
            BigDecimal feeDue,
            BigDecimal totalDue,
            InstallmentServicing servicing
    ) {
        public ScheduleItem {
            if (installmentNumber <= 0) {
                throw new IllegalArgumentException("installmentNumber must be positive.");
            }
            Objects.requireNonNull(dueDate, "dueDate must not be null");
            Objects.requireNonNull(principalDue, "principalDue must not be null");
            Objects.requireNonNull(interestDue, "interestDue must not be null");
            Objects.requireNonNull(feeDue, "feeDue must not be null");
            Objects.requireNonNull(totalDue, "totalDue must not be null");
            Objects.requireNonNull(servicing, "servicing must not be null");
        }
    }

    record ServicingSummary(
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            BigDecimal feePaid,
            BigDecimal totalPaid,
            BigDecimal principalOutstanding,
            BigDecimal interestOutstanding,
            BigDecimal feeOutstanding,
            BigDecimal totalOutstanding,
            LocalDate servicingEvaluationDate,
            LocalDate lastPaymentValueDate,
            LocalDateTime lastPaymentRecordedAt
    ) {
    }

    record InstallmentServicing(
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            BigDecimal feePaid,
            BigDecimal totalPaid,
            BigDecimal principalOutstanding,
            BigDecimal interestOutstanding,
            BigDecimal feeOutstanding,
            BigDecimal totalOutstanding,
            RepaymentInstallmentStatus status,
            LocalDate statusEvaluationDate,
            LocalDate lastPaymentValueDate,
            LocalDateTime lastPaymentRecordedAt
    ) {
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return value;
    }
}
