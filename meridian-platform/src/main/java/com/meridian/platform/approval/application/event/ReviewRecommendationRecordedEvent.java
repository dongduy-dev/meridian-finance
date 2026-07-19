package com.meridian.platform.approval.application.event;

import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewRecommendationRecordedEvent(
        UUID recommendationId,
        UUID loanApplicationId,
        UUID reviewCycleId,
        UUID loanOfficerUserId,
        ReviewRecommendationEventAction action,
        String reason,
        CorrectionReasonCode reasonCode,
        CorrectionPlanRequest correctionPlan,
        LocalDateTime recordedAt,
        BusinessOperationContext operationContext
) {
    public ReviewRecommendationRecordedEvent(
            UUID recommendationId,
            UUID loanApplicationId,
            UUID loanOfficerUserId,
            ReviewRecommendationEventAction action,
            String reason,
            LocalDateTime recordedAt,
            BusinessOperationContext operationContext
    ) {
        this(recommendationId, loanApplicationId, UUID.randomUUID(), loanOfficerUserId,
                action, reason, null, null, recordedAt, operationContext);
    }
}
