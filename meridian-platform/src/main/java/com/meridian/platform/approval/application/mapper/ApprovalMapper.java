package com.meridian.platform.approval.application.mapper;

import com.meridian.platform.approval.application.dto.ApprovalDecisionDto;
import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.application.dto.ReviewRecommendationDto;
import com.meridian.platform.approval.application.event.ApprovalDecisionEventAction;
import com.meridian.platform.approval.application.event.ApprovalDecisionRecordedEvent;
import com.meridian.platform.approval.application.event.ReviewRecommendationEventAction;
import com.meridian.platform.approval.application.event.ReviewRecommendationRecordedEvent;
import com.meridian.platform.approval.domain.model.ApprovalDecision;
import com.meridian.platform.approval.domain.model.ReviewRecommendation;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import org.springframework.stereotype.Component;

@Component
public class ApprovalMapper {

    public ReviewRecommendationDto toDto(ReviewRecommendation recommendation) {
        return new ReviewRecommendationDto(
                recommendation.id(),
                recommendation.loanApplicationId(),
                recommendation.reviewCycleId(),
                recommendation.loanOfficerUserId(),
                recommendation.action().name(),
                recommendation.reason(),
                recommendation.internalNotes(),
                recommendation.reasonCode() == null ? null : recommendation.reasonCode().name(),
                recommendation.submittedAt()
        );
    }

    public ReviewRecommendationRecordedEvent toRecordedEvent(
            ReviewRecommendation recommendation,
            BusinessOperationContext operationContext
    ) {
        return toRecordedEvent(recommendation, operationContext, null);
    }

    public ReviewRecommendationRecordedEvent toRecordedEvent(
            ReviewRecommendation recommendation,
            BusinessOperationContext operationContext,
            CorrectionPlanRequest correctionPlan
    ) {
        return new ReviewRecommendationRecordedEvent(
                recommendation.id(),
                recommendation.loanApplicationId(),
                recommendation.reviewCycleId(),
                recommendation.loanOfficerUserId(),
                ReviewRecommendationEventAction.valueOf(recommendation.action().name()),
                recommendation.reason(),
                recommendation.reasonCode(),
                correctionPlan,
                recommendation.submittedAt(),
                operationContext
        );
    }

    public ApprovalDecisionDto toDto(ApprovalDecision decision) {
        return new ApprovalDecisionDto(
                decision.id(),
                decision.loanApplicationId(),
                decision.reviewRecommendationId(),
                decision.approverUserId(),
                decision.action().name(),
                decision.reason(),
                decision.reasonCode() == null ? null : decision.reasonCode().name(),
                decision.internalNotes(),
                decision.decidedAt()
        );
    }

    public ApprovalDecisionRecordedEvent toRecordedEvent(
            ApprovalDecision decision,
            BusinessOperationContext operationContext
    ) {
        return toRecordedEvent(decision, java.util.UUID.randomUUID(), operationContext, null);
    }

    public ApprovalDecisionRecordedEvent toRecordedEvent(
            ApprovalDecision decision,
            java.util.UUID reviewCycleId,
            BusinessOperationContext operationContext,
            CorrectionPlanRequest correctionPlan
    ) {
        return new ApprovalDecisionRecordedEvent(
                decision.id(),
                decision.loanApplicationId(),
                reviewCycleId,
                decision.reviewRecommendationId(),
                decision.approverUserId(),
                ApprovalDecisionEventAction.valueOf(decision.action().name()),
                decision.reason(),
                decision.reasonCode(),
                correctionPlan,
                decision.decidedAt(),
                operationContext
        );
    }
}
