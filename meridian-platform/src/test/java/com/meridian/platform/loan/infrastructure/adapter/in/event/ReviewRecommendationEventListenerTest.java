package com.meridian.platform.loan.infrastructure.adapter.in.event;

import com.meridian.platform.approval.application.event.ReviewRecommendationEventAction;
import com.meridian.platform.approval.application.event.ReviewRecommendationRecordedEvent;
import com.meridian.platform.loan.application.dto.ApplyReviewRecommendationCommand;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.ApplyReviewRecommendationUseCase;
import com.meridian.platform.loan.domain.model.LoanReviewRecommendationAction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewRecommendationEventListenerTest {

    @Test
    void mapsApprovalEventToLoanOwnedCommand() {
        CapturingUseCase useCase = new CapturingUseCase();
        ReviewRecommendationEventListener listener = new ReviewRecommendationEventListener(useCase);
        UUID recommendationId = UUID.randomUUID();
        UUID loanApplicationId = UUID.randomUUID();
        UUID loanOfficerUserId = UUID.randomUUID();
        LocalDateTime recordedAt = LocalDateTime.now();

        listener.onReviewRecommendationRecorded(new ReviewRecommendationRecordedEvent(
                recommendationId,
                loanApplicationId,
                loanOfficerUserId,
                ReviewRecommendationEventAction.RECOMMEND_REJECTION,
                "not eligible",
                recordedAt
        ));

        assertEquals(recommendationId, useCase.command.recommendationId());
        assertEquals(loanApplicationId, useCase.command.loanApplicationId());
        assertEquals(loanOfficerUserId, useCase.command.loanOfficerUserId());
        assertEquals(LoanReviewRecommendationAction.RECOMMEND_REJECTION, useCase.command.action());
        assertEquals(recordedAt, useCase.command.recommendedAt());
    }

    private static class CapturingUseCase implements ApplyReviewRecommendationUseCase {

        private ApplyReviewRecommendationCommand command;

        @Override
        public LoanApplicationReviewDto applyReviewRecommendation(ApplyReviewRecommendationCommand command) {
            this.command = command;
            return new LoanApplicationReviewDto(command.loanApplicationId(), "APPROVAL_PENDING");
        }
    }
}
