package com.meridian.platform.loan.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ClosedLoanAccountDto(
        UUID loanApplicationId,
        UUID loanAccountId,
        String resultingStatus,
        LocalDateTime closedAt,
        boolean idempotentReplay
) {
    @Override
    public String toString() {
        return "ClosedLoanAccountDto[loanApplicationId=" + loanApplicationId
                + ", loanAccountId=" + loanAccountId
                + ", resultingStatus=" + resultingStatus
                + ", administrativeEvidence=redacted]";
    }
}
