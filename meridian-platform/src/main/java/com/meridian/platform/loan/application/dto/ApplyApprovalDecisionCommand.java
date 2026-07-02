package com.meridian.platform.loan.application.dto;

import com.meridian.platform.loan.domain.model.LoanApprovalDecisionAction;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplyApprovalDecisionCommand(
        UUID loanApplicationId,
        UUID decisionId,
        UUID reviewRecommendationId,
        UUID approverUserId,
        LoanApprovalDecisionAction action,
        LocalDateTime decidedAt
) {
}
