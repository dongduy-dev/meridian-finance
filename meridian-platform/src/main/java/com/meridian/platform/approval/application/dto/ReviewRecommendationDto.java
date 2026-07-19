package com.meridian.platform.approval.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewRecommendationDto(
        UUID recommendationId,
        UUID loanApplicationId,
        UUID reviewCycleId,
        UUID loanOfficerUserId,
        String action,
        String reason,
        String internalNotes,
        String reasonCode,
        LocalDateTime submittedAt
) {
    public ReviewRecommendationDto(
            UUID recommendationId,
            UUID loanApplicationId,
            UUID loanOfficerUserId,
            String action,
            String reason,
            String internalNotes,
            LocalDateTime submittedAt
    ) {
        this(recommendationId, loanApplicationId, UUID.randomUUID(), loanOfficerUserId,
                action, reason, internalNotes, null, submittedAt);
    }
}
