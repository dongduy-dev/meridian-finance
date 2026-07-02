package com.meridian.platform.approval.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaReviewRecommendationRepository extends JpaRepository<ReviewRecommendationJpaEntity, UUID> {

    Optional<ReviewRecommendationJpaEntity> findFirstByLoanApplicationIdOrderBySubmittedAtDesc(UUID loanApplicationId);
}
