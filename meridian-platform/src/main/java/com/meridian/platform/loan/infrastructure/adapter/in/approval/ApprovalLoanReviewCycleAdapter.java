package com.meridian.platform.loan.infrastructure.adapter.in.approval;

import com.meridian.platform.approval.application.spi.loan.ApprovalLoanReviewCycleSpi;
import com.meridian.platform.loan.application.port.in.QueryApprovalLoanReviewCycleUseCase;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ApprovalLoanReviewCycleAdapter implements ApprovalLoanReviewCycleSpi {
    private final QueryApprovalLoanReviewCycleUseCase cycles;

    public ApprovalLoanReviewCycleAdapter(QueryApprovalLoanReviewCycleUseCase cycles) {
        this.cycles = cycles;
    }

    @Override
    public Optional<ActiveReviewCycleSnapshot> findActiveReviewCycle(UUID loanApplicationId) {
        return cycles.findActiveReviewCycle(loanApplicationId).map(cycle -> new ActiveReviewCycleSnapshot(
                cycle.reviewCycleId(), cycle.assignedLoanOfficerUserId()));
    }
}
