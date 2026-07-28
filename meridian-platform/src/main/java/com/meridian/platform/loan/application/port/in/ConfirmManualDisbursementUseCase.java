package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface ConfirmManualDisbursementUseCase {

    Result confirm(Command command);

    record Command(
            UUID requestId,
            UUID loanApplicationId,
            int expectedContractVersion,
            String externalTransferReference,
            LocalDate disbursementValueDate,
            LocalDate firstRepaymentDate
    ) {
        public Command {
            Objects.requireNonNull(requestId, "requestId must not be null");
            Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
            Objects.requireNonNull(externalTransferReference,
                    "externalTransferReference must not be null");
            Objects.requireNonNull(disbursementValueDate,
                    "disbursementValueDate must not be null");
            Objects.requireNonNull(firstRepaymentDate,
                    "firstRepaymentDate must not be null");
            if (expectedContractVersion <= 0) {
                throw new IllegalArgumentException(
                        "expectedContractVersion must be positive."
                );
            }
        }

        @Override
        public String toString() {
            return "Command[requestId=" + requestId
                    + ", loanApplicationId=" + loanApplicationId
                    + ", expectedContractVersion=" + expectedContractVersion
                    + ", disbursementValueDate=" + disbursementValueDate
                    + ", firstRepaymentDate=" + firstRepaymentDate
                    + ", externalTransferReference=redacted]";
        }
    }

    record Result(
            UUID loanApplicationId,
            LoanApplicationStatus applicationStatus,
            UUID loanAccountId,
            String loanAccountNumber,
            LoanAccountStatus loanAccountStatus,
            LocalDateTime activatedAt,
            UUID manualDisbursementId,
            BigDecimal disbursedAmount,
            LocalDate disbursementValueDate,
            LocalDate firstRepaymentDate,
            UUID repaymentScheduleId,
            RepaymentScheduleType scheduleType,
            int scheduleVersion,
            List<ScheduleItem> scheduleItems,
            boolean idempotentReplay
    ) {
        public Result {
            Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
            Objects.requireNonNull(applicationStatus, "applicationStatus must not be null");
            Objects.requireNonNull(loanAccountId, "loanAccountId must not be null");
            Objects.requireNonNull(loanAccountNumber, "loanAccountNumber must not be null");
            Objects.requireNonNull(loanAccountStatus, "loanAccountStatus must not be null");
            Objects.requireNonNull(activatedAt, "activatedAt must not be null");
            Objects.requireNonNull(manualDisbursementId,
                    "manualDisbursementId must not be null");
            Objects.requireNonNull(disbursedAmount, "disbursedAmount must not be null");
            Objects.requireNonNull(disbursementValueDate,
                    "disbursementValueDate must not be null");
            Objects.requireNonNull(firstRepaymentDate,
                    "firstRepaymentDate must not be null");
            Objects.requireNonNull(repaymentScheduleId,
                    "repaymentScheduleId must not be null");
            Objects.requireNonNull(scheduleType, "scheduleType must not be null");
            if (scheduleVersion <= 0) {
                throw new IllegalArgumentException("scheduleVersion must be positive.");
            }
            scheduleItems = List.copyOf(Objects.requireNonNull(
                    scheduleItems,
                    "scheduleItems must not be null"
            ));
        }

        @Override
        public String toString() {
            return "Result[loanApplicationId=" + loanApplicationId
                    + ", applicationStatus=" + applicationStatus
                    + ", loanAccountId=" + loanAccountId
                    + ", loanAccountStatus=" + loanAccountStatus
                    + ", manualDisbursementId=" + manualDisbursementId
                    + ", repaymentScheduleId=" + repaymentScheduleId
                    + ", idempotentReplay=" + idempotentReplay
                    + ", transferAndFinancialEvidence=redacted]";
        }
    }

    record ScheduleItem(
            UUID id,
            UUID sourceLoanContractRepaymentItemId,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal principalDue,
            BigDecimal interestDue,
            BigDecimal feeDue,
            BigDecimal totalDue
    ) {
        public ScheduleItem {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(sourceLoanContractRepaymentItemId,
                    "sourceLoanContractRepaymentItemId must not be null");
            Objects.requireNonNull(dueDate, "dueDate must not be null");
            Objects.requireNonNull(principalDue, "principalDue must not be null");
            Objects.requireNonNull(interestDue, "interestDue must not be null");
            Objects.requireNonNull(feeDue, "feeDue must not be null");
            Objects.requireNonNull(totalDue, "totalDue must not be null");
            if (installmentNumber <= 0) {
                throw new IllegalArgumentException("installmentNumber must be positive.");
            }
        }

        @Override
        public String toString() {
            return "ScheduleItem[id=" + id
                    + ", sourceLoanContractRepaymentItemId="
                    + sourceLoanContractRepaymentItemId
                    + ", installmentNumber=" + installmentNumber
                    + ", dueDate=" + dueDate
                    + ", financialEvidence=redacted]";
        }
    }
}
