package com.meridian.platform.loan.application.port.in;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueryApprovalLoanReviewHistoryUseCase {
    Optional<HistorySnapshot> findHistory(UUID loanApplicationId);

    record HistorySnapshot(UUID loanApplicationId, String applicationNumber, String applicationStatus,
                           List<QueryApprovalLoanCaseUseCase.ReviewCycleSnapshot> cycles) {
        public HistorySnapshot {
            cycles = List.copyOf(cycles);
        }
    }
}
