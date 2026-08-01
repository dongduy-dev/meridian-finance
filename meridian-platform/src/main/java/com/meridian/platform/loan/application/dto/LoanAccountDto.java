package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record LoanAccountDto(
        UUID loanApplicationId,
        UUID loanAccountId,
        String accountNumber,
        String status,
        LocalDateTime activatedAt,
        BigDecimal originatedPrincipal,
        int approvedTermMonths,
        BigDecimal totalInterest,
        BigDecimal totalFee,
        BigDecimal totalRepayment,
        ServicingSummaryDto servicing,
        DestinationSummaryDto disbursementDestination,
        FinalScheduleDto finalRepaymentSchedule
) {
    @Override
    public String toString() {
        return "LoanAccountDto[loanApplicationId=" + loanApplicationId
                + ", loanAccountId=" + loanAccountId
                + ", status=" + status
                + ", destinationAndFinancialEvidence=redacted]";
    }

    public record DestinationSummaryDto(
            String bankCode,
            String bankName,
            String accountHolderName,
            String maskedAccountNumber
    ) {
        @Override
        public String toString() {
            return "DestinationSummaryDto[destination=redacted]";
        }
    }

    public record FinalScheduleDto(
            UUID scheduleId,
            String scheduleType,
            int version,
            LocalDate firstDueDate,
            LocalDate lastDueDate,
            List<ScheduleItemDto> items
    ) {
        public FinalScheduleDto {
            items = List.copyOf(items);
        }
    }

    public record ScheduleItemDto(
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal principalDue,
            BigDecimal interestDue,
            BigDecimal feeDue,
            BigDecimal totalDue,
            InstallmentServicingDto servicing
    ) {
    }

    public record ServicingSummaryDto(
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

    public record InstallmentServicingDto(
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            BigDecimal feePaid,
            BigDecimal totalPaid,
            BigDecimal principalOutstanding,
            BigDecimal interestOutstanding,
            BigDecimal feeOutstanding,
            BigDecimal totalOutstanding,
            String status,
            LocalDate statusEvaluationDate,
            LocalDate lastPaymentValueDate,
            LocalDateTime lastPaymentRecordedAt
    ) {
    }
}
