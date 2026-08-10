package com.meridian.platform.loan.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CancelledLoanApplicationDto(
        UUID loanApplicationId,
        String resultingStatus,
        LocalDateTime cancelledAt,
        boolean idempotentReplay
) {
    @Override
    public String toString() {
        return "CancelledLoanApplicationDto[loanApplicationId=" + loanApplicationId
                + ", resultingStatus=" + resultingStatus
                + ", cancellationEvidence=redacted]";
    }
}
