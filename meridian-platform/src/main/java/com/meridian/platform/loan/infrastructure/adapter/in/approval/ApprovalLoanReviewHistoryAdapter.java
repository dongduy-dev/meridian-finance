package com.meridian.platform.loan.infrastructure.adapter.in.approval;

import com.meridian.platform.approval.application.spi.loan.ApprovalLoanCaseSpi.ReviewCycleSnapshot;
import com.meridian.platform.approval.application.spi.loan.ApprovalLoanReviewHistorySpi;
import com.meridian.platform.loan.application.port.in.QueryApprovalLoanReviewHistoryUseCase;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ApprovalLoanReviewHistoryAdapter implements ApprovalLoanReviewHistorySpi {
    private final QueryApprovalLoanReviewHistoryUseCase history;

    public ApprovalLoanReviewHistoryAdapter(QueryApprovalLoanReviewHistoryUseCase history) {
        this.history = history;
    }

    @Override
    public Optional<HistorySnapshot> findHistory(UUID loanApplicationId) {
        return history.findHistory(loanApplicationId).map(value -> new HistorySnapshot(
                value.loanApplicationId(), value.applicationNumber(), value.applicationStatus(),
                value.cycles().stream().map(cycle -> new ReviewCycleSnapshot(
                        cycle.reviewCycleId(), cycle.cycleNumber(), cycle.assignedLoanOfficerUserId(),
                        cycle.status(), cycle.startedAt(), cycle.endedAt())).toList()));
    }
}
