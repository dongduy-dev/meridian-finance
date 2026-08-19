package com.meridian.platform.loan.application.dto;

import java.util.UUID;

public record CollateralLoanVerificationStartDto(
        UUID verificationId,
        UUID loanApplicationId,
        String status,
        String productVerificationResult,
        CollateralAssessmentSnapshotDto collateral
) {
}
