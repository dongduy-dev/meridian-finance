package com.meridian.platform.approval.application.dto;

import com.meridian.platform.approval.domain.model.ApprovalDecisionAction;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ApprovalDecisionRequest(
        @NotNull
        ApprovalDecisionAction action,

        @Size(max = 2000)
        String reason,

        @Size(max = 2000)
        String internalNotes,

        @NotNull
        UUID expectedReviewRecommendationId,

        @NotNull
        UUID expectedReviewCycleId,

        CorrectionReasonCode reasonCode,

        CorrectionPlanRequest correctionPlan
) {
    public ApprovalDecisionRequest(
            ApprovalDecisionAction action,
            String reason,
            String internalNotes,
            UUID expectedReviewRecommendationId,
            UUID expectedReviewCycleId
    ) {
        this(action, reason, internalNotes, expectedReviewRecommendationId, expectedReviewCycleId, null, null);
    }
}
