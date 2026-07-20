package com.meridian.platform.loan.application.dto;

import java.util.UUID;

public record LoanApplicationReviewDto(
        UUID loanApplicationId,
        String status,
        UUID activeReviewCycleId
) {
    public LoanApplicationReviewDto(UUID loanApplicationId, String status) {
        this(loanApplicationId, status, null);
    }
}
