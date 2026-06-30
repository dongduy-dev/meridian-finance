package com.meridian.platform.approval.application.dto;

import com.meridian.platform.approval.domain.model.ReviewRecommendationAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRecommendationRequest(
        @NotNull
        ReviewRecommendationAction action,

        @Size(max = 2000)
        String reason,

        @Size(max = 2000)
        String internalNotes
) {
}
