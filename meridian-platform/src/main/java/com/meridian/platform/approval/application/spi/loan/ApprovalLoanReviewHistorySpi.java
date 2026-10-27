package com.meridian.platform.approval.application.spi.loan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalLoanReviewHistorySpi {
    Optional<HistorySnapshot> findHistory(UUID loanApplicationId);

    record HistorySnapshot(UUID loanApplicationId, String applicationNumber, String applicationStatus,
                           List<ApprovalLoanCaseSpi.ReviewCycleSnapshot> cycles) {
        public HistorySnapshot {
            cycles = List.copyOf(cycles);
        }
    }
}
