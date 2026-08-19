package com.meridian.platform.loan.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CollateralLoanVerificationDto(
        UUID verificationId,
        UUID loanApplicationId,
        String status,
        String productVerificationResult,
        LocalDateTime reviewedAt
) {
}
