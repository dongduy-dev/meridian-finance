package com.meridian.platform.loan.application.dto;

import com.meridian.platform.loan.domain.model.LoanReviewRecommendationAction;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplyReviewRecommendationCommand(
        UUID loanApplicationId,
        UUID recommendationId,
        UUID loanOfficerUserId,
        LoanReviewRecommendationAction action,
        LocalDateTime recommendedAt
) {
}
