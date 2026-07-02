package com.meridian.platform.approval.application.mapper;

import com.meridian.platform.approval.application.dto.ApprovalDecisionDto;
import com.meridian.platform.approval.application.dto.ReviewRecommendationDto;
import com.meridian.platform.approval.application.event.ApprovalDecisionEventAction;
import com.meridian.platform.approval.application.event.ApprovalDecisionRecordedEvent;
import com.meridian.platform.approval.application.event.ReviewRecommendationEventAction;
import com.meridian.platform.approval.application.event.ReviewRecommendationRecordedEvent;
import com.meridian.platform.approval.domain.model.ApprovalDecision;
import com.meridian.platform.approval.domain.model.ReviewRecommendation;
import org.springframework.stereotype.Component;

@Component
public class ApprovalMapper {

    public ReviewRecommendationDto toDto(ReviewRecommendation recommendation) {
        return new ReviewRecommendationDto(
                recommendation.id(),
                recommendation.loanApplicationId(),
                recommendation.loanOfficerUserId(),
                recommendation.action().name(),
                recommendation.reason(),
                recommendation.internalNotes(),
                recommendation.submittedAt()
        );
    }

    public ReviewRecommendationRecordedEvent toRecordedEvent(ReviewRecommendation recommendation) {
        return new ReviewRecommendationRecordedEvent(
                recommendation.id(),
                recommendation.loanApplicationId(),
                recommendation.loanOfficerUserId(),
                ReviewRecommendationEventAction.valueOf(recommendation.action().name()),
                recommendation.reason(),
                recommendation.submittedAt()
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
                decision.internalNotes(),
                decision.decidedAt()
        );
    }

    public ApprovalDecisionRecordedEvent toRecordedEvent(ApprovalDecision decision) {
        return new ApprovalDecisionRecordedEvent(
                decision.id(),
                decision.loanApplicationId(),
                decision.reviewRecommendationId(),
                decision.approverUserId(),
                ApprovalDecisionEventAction.valueOf(decision.action().name()),
                decision.reason(),
                decision.decidedAt()
        );
    }
}
