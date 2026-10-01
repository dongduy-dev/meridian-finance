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
        String reasonCode,
        LocalDateTime submittedAt
) {
    public ReviewRecommendationDto(
            UUID recommendationId,
            UUID loanApplicationId,
            UUID loanOfficerUserId,
            String action,
            String reason,
            LocalDateTime submittedAt
    ) {
        this(recommendationId, loanApplicationId, UUID.randomUUID(), loanOfficerUserId,
                action, reason, null, submittedAt);
    }
}
