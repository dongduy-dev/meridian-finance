package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RecordRepaymentDto(
        UUID loanApplicationId,
        UUID loanAccountId,
        UUID repaymentTransactionId,
        UUID finalScheduleId,
        BigDecimal receivedAmount,
        LocalDate paymentValueDate,
        LocalDateTime recordedAt,
        BigDecimal principalAllocated,
        BigDecimal principalReleased,
        String resultingLoanAccountStatus,
        AccountBalanceDto accountBalance,
        List<AllocationDto> allocations,
        List<InstallmentOutcomeDto> affectedInstallments,
        boolean idempotentReplay
) {
    public RecordRepaymentDto {
        allocations = List.copyOf(allocations);
        affectedInstallments = List.copyOf(affectedInstallments);
    }

    @Override
    public String toString() {
        return "RecordRepaymentDto[loanApplicationId=" + loanApplicationId
                + ", loanAccountId=" + loanAccountId
                + ", repaymentTransactionId=" + repaymentTransactionId
                + ", financialEvidence=redacted, idempotentReplay="
                + idempotentReplay + "]";
    }

    public record AllocationDto(
            int sequence,
            UUID repaymentScheduleItemId,
            int installmentNumber,
            String component,
            BigDecimal allocatedAmount
    ) {
    }

    public record InstallmentOutcomeDto(
            UUID repaymentScheduleItemId,
            int installmentNumber,
            LocalDate dueDate,
            String previousStatus,
            String resultingStatus,
            LocalDate evaluationDate,
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
            boolean statusChanged
    ) {
    }

    public record AccountBalanceDto(
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
            String status
    ) {
    }
}
