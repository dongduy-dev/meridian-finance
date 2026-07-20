package com.meridian.platform.approval.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApprovalDecisionDto(
        UUID decisionId,
        UUID loanApplicationId,
        UUID reviewRecommendationId,
        UUID approverUserId,
        String action,
        String reason,
        String reasonCode,
        String internalNotes,
        LocalDateTime decidedAt
) {
    public ApprovalDecisionDto(
            UUID decisionId,
            UUID loanApplicationId,
            UUID reviewRecommendationId,
            UUID approverUserId,
            String action,
            String reason,
            String internalNotes,
            LocalDateTime decidedAt
    ) {
        this(decisionId, loanApplicationId, reviewRecommendationId, approverUserId,
                action, reason, null, internalNotes, decidedAt);
    }
}
