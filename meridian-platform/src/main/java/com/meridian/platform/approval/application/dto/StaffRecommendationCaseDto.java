package com.meridian.platform.approval.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffRecommendationCaseDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String productType,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        String applicationStatus,
        LocalDateTime submittedAt,
        EvidenceDto evidence,
        RecommendationDto recommendation,
        boolean recommendationAvailable,
        List<String> correctionReasonCodes,
        List<CorrectionOptionDto> correctionOptions
) {
    public StaffRecommendationCaseDto {
        correctionReasonCodes = List.copyOf(correctionReasonCodes);
        correctionOptions = List.copyOf(correctionOptions);
    }

    public record EvidenceDto(
            boolean uploadComplete,
            boolean processingReady,
            String productVerificationResult,
            boolean readyForDecision,
            ReviewCycleDto currentReviewCycle
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

    public record RecommendationDto(
            UUID recommendationId,
            UUID reviewCycleId,
            String action,
            String reason,
            String reasonCode,
            LocalDateTime submittedAt
    ) {
    }

    public record CorrectionOptionDto(
            String documentType,
            UUID checklistItemId,
            UUID currentDocumentVersionId,
            List<String> allowedScopes
    ) {
        public CorrectionOptionDto {
            allowedScopes = List.copyOf(allowedScopes);
        }
    }
}
