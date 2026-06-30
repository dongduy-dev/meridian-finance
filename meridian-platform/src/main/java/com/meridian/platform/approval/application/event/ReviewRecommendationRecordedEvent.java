package com.meridian.platform.approval.application.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewRecommendationRecordedEvent(
        UUID recommendationId,
        UUID loanApplicationId,
        UUID loanOfficerUserId,
        ReviewRecommendationEventAction action,
        String reason,
        LocalDateTime recordedAt
) {
}
