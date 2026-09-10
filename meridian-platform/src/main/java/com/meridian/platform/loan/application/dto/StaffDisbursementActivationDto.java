package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffDisbursementActivationDto(
        UUID loanAccountId,
        String loanAccountNumber,
        String loanAccountStatus,
        LocalDateTime activatedAt,
        BigDecimal disbursedAmount,
        LocalDate disbursementValueDate,
        LocalDate firstRepaymentDate,
        UUID repaymentScheduleId,
        String scheduleType,
        int scheduleVersion,
        List<ScheduleItemDto> scheduleItems
) {
    public StaffDisbursementActivationDto {
        scheduleItems = List.copyOf(scheduleItems);
    }

    @Override
    public String toString() {
        return "StaffDisbursementActivationDto[loanAccountId=" + loanAccountId
                + ", loanAccountStatus=" + loanAccountStatus
                + ", financialEvidence=redacted]";
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
