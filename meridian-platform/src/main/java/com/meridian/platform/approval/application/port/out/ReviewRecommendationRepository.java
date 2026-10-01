package com.meridian.platform.approval.application.port.out;

import com.meridian.platform.approval.domain.model.ReviewRecommendation;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ReviewRecommendationRepository {
    List<ReviewRecommendation> findByLoanApplicationIdOrderBySubmittedAtAsc(UUID loanApplicationId);


    ReviewRecommendation save(ReviewRecommendation recommendation);

    Optional<ReviewRecommendation> findLatestByLoanApplicationId(UUID loanApplicationId);

    Optional<ReviewRecommendation> findByReviewCycleId(UUID reviewCycleId);
}
