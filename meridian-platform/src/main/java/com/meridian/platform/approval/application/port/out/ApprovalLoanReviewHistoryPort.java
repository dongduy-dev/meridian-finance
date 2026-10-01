package com.meridian.platform.approval.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalLoanReviewHistoryPort {
    Optional<HistorySnapshot> findHistory(UUID loanApplicationId);

    record HistorySnapshot(UUID loanApplicationId, String applicationNumber, String applicationStatus,
                           List<ApprovalLoanCasePort.ReviewCycleSnapshot> cycles) {
        public HistorySnapshot {
            cycles = List.copyOf(cycles);
        }
    }
}
