package com.meridian.platform.loan.infrastructure.adapter.in.event;

import com.meridian.platform.approval.application.event.ApprovalDecisionEventAction;
import com.meridian.platform.approval.application.event.ApprovalDecisionRecordedEvent;
import com.meridian.platform.loan.application.dto.ApplyApprovalDecisionCommand;
import com.meridian.platform.loan.application.port.in.ApplyApprovalDecisionUseCase;
import com.meridian.platform.loan.domain.model.LoanApprovalDecisionAction;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ApprovalDecisionEventListener {

    private final ApplyApprovalDecisionUseCase applyApprovalDecisionUseCase;

    public ApprovalDecisionEventListener(ApplyApprovalDecisionUseCase applyApprovalDecisionUseCase) {
        this.applyApprovalDecisionUseCase = applyApprovalDecisionUseCase;
    }

    @EventListener
    public void onApprovalDecisionRecorded(ApprovalDecisionRecordedEvent event) {
        // Intentional synchronous listener: Loan status failures must roll back the decision transaction.
        applyApprovalDecisionUseCase.applyApprovalDecision(new ApplyApprovalDecisionCommand(
                event.loanApplicationId(),
                event.decisionId(),
                event.reviewRecommendationId(),
                event.approverUserId(),
                toLoanAction(event.action()),
                event.decidedAt()
        ));
    }

    private LoanApprovalDecisionAction toLoanAction(ApprovalDecisionEventAction action) {
        return switch (action) {
            case APPROVE -> LoanApprovalDecisionAction.APPROVE;
            case REJECT -> LoanApprovalDecisionAction.REJECT;
            case RETURN_TO_LOAN_OFFICER_REVIEW -> LoanApprovalDecisionAction.RETURN_TO_LOAN_OFFICER_REVIEW;
            case REQUEST_CUSTOMER_OR_STAFF_CORRECTION ->
                    LoanApprovalDecisionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION;
        };
    }
}
