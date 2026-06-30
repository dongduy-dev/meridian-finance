package com.meridian.platform.loan.application.dto;

import java.util.UUID;

public record LoanApplicationReviewDto(
        UUID loanApplicationId,
        String status
) {
}
