package com.meridian.platform.approval.application.event;

import com.meridian.platform.shared.application.operation.BusinessOperationContext;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewRecommendationRecordedEvent(
        UUID recommendationId,
        UUID loanApplicationId,
        UUID loanOfficerUserId,
        ReviewRecommendationEventAction action,
        String reason,
        LocalDateTime recordedAt,
        BusinessOperationContext operationContext
) {
}
