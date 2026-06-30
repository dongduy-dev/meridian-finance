package com.meridian.platform.approval.application.port.in;

import com.meridian.platform.approval.application.dto.ReviewRecommendationDto;
import com.meridian.platform.approval.application.dto.ReviewRecommendationRequest;

import java.util.UUID;

public interface SubmitReviewRecommendationUseCase {

    ReviewRecommendationDto submitReviewRecommendation(
            UUID loanApplicationId,
            ReviewRecommendationRequest request
    );
}
