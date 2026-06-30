package com.meridian.platform.loan.infrastructure.adapter.in.event;

import com.meridian.platform.approval.application.event.ReviewRecommendationEventAction;
import com.meridian.platform.approval.application.event.ReviewRecommendationRecordedEvent;
import com.meridian.platform.loan.application.dto.ApplyReviewRecommendationCommand;
import com.meridian.platform.loan.application.port.in.ApplyReviewRecommendationUseCase;
import com.meridian.platform.loan.domain.model.LoanReviewRecommendationAction;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ReviewRecommendationEventListener {

    private final ApplyReviewRecommendationUseCase applyReviewRecommendationUseCase;

    public ReviewRecommendationEventListener(ApplyReviewRecommendationUseCase applyReviewRecommendationUseCase) {
        this.applyReviewRecommendationUseCase = applyReviewRecommendationUseCase;
    }

    @EventListener
    public void onReviewRecommendationRecorded(ReviewRecommendationRecordedEvent event) {
        // Intentional synchronous listener: Loan status failures must roll back the recommendation transaction.
        applyReviewRecommendationUseCase.applyReviewRecommendation(new ApplyReviewRecommendationCommand(
                event.loanApplicationId(),
                event.recommendationId(),
                event.loanOfficerUserId(),
                toLoanAction(event.action()),
                event.recordedAt()
        ));
    }

    private LoanReviewRecommendationAction toLoanAction(ReviewRecommendationEventAction action) {
        return switch (action) {
            case RECOMMEND_APPROVAL -> LoanReviewRecommendationAction.RECOMMEND_APPROVAL;
            case RECOMMEND_REJECTION -> LoanReviewRecommendationAction.RECOMMEND_REJECTION;
            case RETURN_TO_CUSTOMER_REVISION -> LoanReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION;
            case REQUEST_STAFF_CORRECTION -> LoanReviewRecommendationAction.REQUEST_STAFF_CORRECTION;
        };
    }
}
