package com.meridian.platform.loan.application.dto;

import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.loan.domain.model.LoanApprovalDecisionAction;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplyApprovalDecisionCommand(
        UUID loanApplicationId,
        UUID decisionId,
        UUID reviewCycleId,
        UUID reviewRecommendationId,
        UUID approverUserId,
        LoanApprovalDecisionAction action,
        String reason,
        CorrectionReasonCode reasonCode,
        CorrectionPlanRequest correctionPlan,
        LocalDateTime decidedAt,
        BusinessOperationContext operationContext
) {
    public ApplyApprovalDecisionCommand(
            UUID loanApplicationId,
            UUID decisionId,
            UUID reviewRecommendationId,
            UUID approverUserId,
            LoanApprovalDecisionAction action,
            String reason,
            LocalDateTime decidedAt,
            BusinessOperationContext operationContext
    ) {
        this(loanApplicationId, decisionId, null, reviewRecommendationId,
                approverUserId, action, reason, null, null, decidedAt, operationContext);
    }
}
