package com.meridian.platform.approval.infrastructure.adapter.out.persistence;

import com.meridian.platform.approval.application.port.out.ReviewRecommendationRepository;
import com.meridian.platform.approval.domain.model.ReviewRecommendation;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ReviewRecommendationRepositoryAdapter implements ReviewRecommendationRepository {

    private final JpaReviewRecommendationRepository jpaReviewRecommendationRepository;

    public ReviewRecommendationRepositoryAdapter(JpaReviewRecommendationRepository jpaReviewRecommendationRepository) {
        this.jpaReviewRecommendationRepository = jpaReviewRecommendationRepository;
    }

    @Override
    public ReviewRecommendation save(ReviewRecommendation recommendation) {
        return jpaReviewRecommendationRepository.save(new ReviewRecommendationJpaEntity(recommendation))
                .toDomain();
    }

    @Override
    public Optional<ReviewRecommendation> findLatestByLoanApplicationId(UUID loanApplicationId) {
        return jpaReviewRecommendationRepository.findFirstByLoanApplicationIdOrderBySubmittedAtDesc(loanApplicationId)
                .map(ReviewRecommendationJpaEntity::toDomain);
    }
}
