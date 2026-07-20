package com.meridian.platform.approval.infrastructure.adapter.out.persistence;

import com.meridian.platform.approval.application.port.out.ReviewRecommendationRepository;
import com.meridian.platform.approval.domain.model.ReviewRecommendation;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;
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
        try {
            return jpaReviewRecommendationRepository.saveAndFlush(
                    new ReviewRecommendationJpaEntity(recommendation)).toDomain();
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueConstraint(exception, "uq_review_recommendations_cycle")) {
                throw new BusinessStateConflictException(
                        "LOAN_RECOMMENDATION_NOT_ALLOWED",
                        "A recommendation was already recorded for this review cycle."
                );
            }
            throw exception;
        }
    }

    private boolean isUniqueConstraint(Throwable exception, String constraint) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains(constraint)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Override
    public Optional<ReviewRecommendation> findLatestByLoanApplicationId(UUID loanApplicationId) {
        return jpaReviewRecommendationRepository.findFirstByLoanApplicationIdOrderBySubmittedAtDesc(loanApplicationId)
                .map(ReviewRecommendationJpaEntity::toDomain);
    }
}
