package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ManualDisbursementConfirmationDto(
        UUID loanApplicationId,
        String applicationStatus,
        UUID loanAccountId,
        String loanAccountNumber,
        String loanAccountStatus,
        LocalDateTime activatedAt,
        UUID manualDisbursementId,
        BigDecimal disbursedAmount,
        LocalDate disbursementValueDate,
        LocalDate firstRepaymentDate,
        UUID repaymentScheduleId,
        String scheduleType,
        int scheduleVersion,
        List<ScheduleItemDto> scheduleItems,
        boolean idempotentReplay
) {
    public ManualDisbursementConfirmationDto {
        scheduleItems = List.copyOf(scheduleItems);
    }

    @Override
    public String toString() {
        return "ManualDisbursementConfirmationDto[loanApplicationId=" + loanApplicationId
                + ", loanAccountId=" + loanAccountId
                + ", applicationStatus=" + applicationStatus
                + ", idempotentReplay=" + idempotentReplay
                + ", transferAndFinancialEvidence=redacted]";
    }

    public record ScheduleItemDto(
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal principalDue,
            BigDecimal interestDue,
            BigDecimal feeDue,
            BigDecimal totalDue
    ) {
    }
}
