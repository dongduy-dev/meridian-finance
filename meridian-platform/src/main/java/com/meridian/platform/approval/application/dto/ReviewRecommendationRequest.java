package com.meridian.platform.approval.application.dto;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.approval.domain.model.ReviewRecommendationAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReviewRecommendationRequest(
        @NotNull
        ReviewRecommendationAction action,

        @Size(max = 2000)
        String reason,

        @Size(max = 2000)
        String internalNotes,

        @NotNull
        UUID expectedReviewCycleId,

        CorrectionReasonCode reasonCode,

        CorrectionPlanRequest correctionPlan
) {
    public ReviewRecommendationRequest(
            ReviewRecommendationAction action,
            String reason,
            String internalNotes,
            UUID expectedReviewCycleId
    ) {
        this(action, reason, internalNotes, expectedReviewCycleId, null, null);
    }
}
