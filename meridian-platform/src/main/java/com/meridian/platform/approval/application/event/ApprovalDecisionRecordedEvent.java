package com.meridian.platform.approval.application.event;

import com.meridian.platform.shared.application.operation.BusinessOperationContext;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApprovalDecisionRecordedEvent(
        UUID decisionId,
        UUID loanApplicationId,
        UUID reviewRecommendationId,
        UUID approverUserId,
        ApprovalDecisionEventAction action,
        String reason,
        LocalDateTime decidedAt,
        BusinessOperationContext operationContext
) {
}
