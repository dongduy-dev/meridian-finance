package com.meridian.platform.loan.application.dto;

import com.meridian.platform.loan.application.dto.correction.LoanCorrectionPlan;
import com.meridian.platform.loan.domain.model.LoanCorrectionReasonCode;
import com.meridian.platform.loan.domain.model.LoanReviewRecommendationAction;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplyReviewRecommendationCommand(
        UUID loanApplicationId,
        UUID recommendationId,
        UUID reviewCycleId,
        UUID loanOfficerUserId,
        LoanReviewRecommendationAction action,
        String reason,
        LoanCorrectionReasonCode reasonCode,
        LoanCorrectionPlan correctionPlan,
        LocalDateTime recommendedAt,
        BusinessOperationContext operationContext
) {
    public ApplyReviewRecommendationCommand(
            UUID loanApplicationId,
            UUID recommendationId,
            UUID loanOfficerUserId,
            LoanReviewRecommendationAction action,
            String reason,
            LocalDateTime recommendedAt,
            BusinessOperationContext operationContext
    ) {
        this(loanApplicationId, recommendationId, UUID.randomUUID(), loanOfficerUserId,
                action, reason, null, null, recommendedAt, operationContext);
    }
}
