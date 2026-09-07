package com.meridian.platform.approval.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffDecisionCaseDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String productType,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        String applicationStatus,
        LocalDateTime submittedAt,
        StaffRecommendationCaseDto.EvidenceDto evidence,
        StaffRecommendationCaseDto.RecommendationDto recommendation,
        boolean makerCheckerEligible,
        boolean decisionAvailable,
        DecisionDto latestDecision,
        List<DecisionDto> decisionHistory,
        List<String> correctionReasonCodes,
        List<StaffRecommendationCaseDto.CorrectionOptionDto> correctionOptions
) {
    public StaffDecisionCaseDto {
        decisionHistory = List.copyOf(decisionHistory);
        correctionReasonCodes = List.copyOf(correctionReasonCodes);
        correctionOptions = List.copyOf(correctionOptions);
    }

    public record DecisionDto(
            UUID decisionId,
            UUID reviewRecommendationId,
            String action,
            String reason,
            String reasonCode,
            LocalDateTime decidedAt
    ) {
    }
}
