package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record StaffLoanApplicationReviewDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String productType,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        String applicationStatus,
        LocalDateTime submittedAt,
        DocumentReadinessDto documentReadiness,
        ProductReadinessDto productReadiness,
        boolean reviewStartAvailable,
        ReviewCycleDto currentReviewCycle
) {
    public record DocumentReadinessDto(boolean uploadComplete, boolean processingReady) {
    }

    public record ProductReadinessDto(
            String productVerificationResult,
            boolean readyForReview
    ) {
    }

    public record ReviewCycleDto(
            UUID reviewCycleId,
            int cycleNumber,
            String status,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
    }
}
