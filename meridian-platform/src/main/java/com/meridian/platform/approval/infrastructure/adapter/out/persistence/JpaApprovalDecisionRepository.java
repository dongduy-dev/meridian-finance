package com.meridian.platform.approval.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaApprovalDecisionRepository extends JpaRepository<ApprovalDecisionJpaEntity, UUID> {

    Optional<ApprovalDecisionJpaEntity> findByReviewRecommendationId(UUID reviewRecommendationId);

    List<ApprovalDecisionJpaEntity> findByLoanApplicationIdOrderByDecidedAtDescIdDesc(UUID loanApplicationId);
}
