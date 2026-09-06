package com.meridian.platform.approval.application.port.out;

import com.meridian.platform.approval.domain.model.ApprovalDecision;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalDecisionRepository {

    ApprovalDecision save(ApprovalDecision approvalDecision);

    Optional<ApprovalDecision> findByReviewRecommendationId(UUID reviewRecommendationId);

    List<ApprovalDecision> findByLoanApplicationIdOrderByDecidedAtDesc(UUID loanApplicationId);
}
