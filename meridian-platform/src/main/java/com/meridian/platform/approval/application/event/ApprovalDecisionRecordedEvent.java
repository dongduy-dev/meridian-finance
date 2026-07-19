package com.meridian.platform.approval.application.event;

import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApprovalDecisionRecordedEvent(
        UUID decisionId,
        UUID loanApplicationId,
        UUID reviewCycleId,
        UUID reviewRecommendationId,
        UUID approverUserId,
        ApprovalDecisionEventAction action,
        String reason,
        CorrectionReasonCode reasonCode,
        CorrectionPlanRequest correctionPlan,
        LocalDateTime decidedAt,
        BusinessOperationContext operationContext
) {
    public ApprovalDecisionRecordedEvent(
            UUID decisionId,
            UUID loanApplicationId,
            UUID reviewRecommendationId,
            UUID approverUserId,
            ApprovalDecisionEventAction action,
            String reason,
            LocalDateTime decidedAt,
            BusinessOperationContext operationContext
    ) {
        this(decisionId, loanApplicationId, UUID.randomUUID(), reviewRecommendationId,
                approverUserId, action, reason, null, null, decidedAt, operationContext);
    }
}
