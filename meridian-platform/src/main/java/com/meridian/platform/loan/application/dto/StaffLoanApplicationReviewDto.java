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
        StaffActorDto assignedLoanOfficer,
        ReviewCycleDto currentReviewCycle
) {
    public StaffLoanApplicationReviewDto(
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
        this(loanApplicationId, applicationNumber, productCode, productType, requestedAmount,
                requestedTermMonths, applicationStatus, submittedAt, documentReadiness,
                productReadiness, reviewStartAvailable, null, currentReviewCycle);
    }

    public record StaffActorDto(UUID userId, String displayName, String email) {
    }

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
            StaffActorDto assignedLoanOfficer,
            String status,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
        public ReviewCycleDto(
                UUID reviewCycleId,
                int cycleNumber,
                String status,
                LocalDateTime startedAt,
                LocalDateTime endedAt
        ) {
            this(reviewCycleId, cycleNumber, null, status, startedAt, endedAt);
        }
    }
}
