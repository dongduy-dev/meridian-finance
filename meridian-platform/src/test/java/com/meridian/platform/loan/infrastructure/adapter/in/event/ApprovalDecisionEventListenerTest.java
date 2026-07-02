package com.meridian.platform.loan.infrastructure.adapter.in.event;

import com.meridian.platform.approval.application.event.ApprovalDecisionEventAction;
import com.meridian.platform.approval.application.event.ApprovalDecisionRecordedEvent;
import com.meridian.platform.loan.application.dto.ApplyApprovalDecisionCommand;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.ApplyApprovalDecisionUseCase;
import com.meridian.platform.loan.domain.model.LoanApprovalDecisionAction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApprovalDecisionEventListenerTest {

    @Test
    void mapsApprovalEventToLoanOwnedCommand() {
        CapturingUseCase useCase = new CapturingUseCase();
        ApprovalDecisionEventListener listener = new ApprovalDecisionEventListener(useCase);
        UUID decisionId = UUID.randomUUID();
        UUID loanApplicationId = UUID.randomUUID();
        UUID recommendationId = UUID.randomUUID();
        UUID approverUserId = UUID.randomUUID();
        LocalDateTime decidedAt = LocalDateTime.now();

        listener.onApprovalDecisionRecorded(new ApprovalDecisionRecordedEvent(
                decisionId,
                loanApplicationId,
                recommendationId,
                approverUserId,
                ApprovalDecisionEventAction.REJECT,
                "not eligible",
                decidedAt
        ));

        assertEquals(decisionId, useCase.command.decisionId());
        assertEquals(loanApplicationId, useCase.command.loanApplicationId());
        assertEquals(recommendationId, useCase.command.reviewRecommendationId());
        assertEquals(approverUserId, useCase.command.approverUserId());
        assertEquals(LoanApprovalDecisionAction.REJECT, useCase.command.action());
        assertEquals(decidedAt, useCase.command.decidedAt());
    }

    private static class CapturingUseCase implements ApplyApprovalDecisionUseCase {

        private ApplyApprovalDecisionCommand command;

        @Override
        public LoanApplicationReviewDto applyApprovalDecision(ApplyApprovalDecisionCommand command) {
            this.command = command;
            return new LoanApplicationReviewDto(command.loanApplicationId(), "REJECTED");
        }
    }
}
