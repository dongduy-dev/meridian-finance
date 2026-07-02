package com.meridian.platform.approval.application.port.out;

import com.meridian.platform.approval.domain.model.ReviewRecommendation;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRecommendationRepository {

    ReviewRecommendation save(ReviewRecommendation recommendation);

    Optional<ReviewRecommendation> findLatestByLoanApplicationId(UUID loanApplicationId);
}
