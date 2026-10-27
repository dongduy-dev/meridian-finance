package com.meridian.platform.approval.application.spi.loan;

import java.util.Optional;
import java.util.UUID;

public interface ApprovalLoanReviewCycleSpi {
    Optional<ActiveReviewCycleSnapshot> findActiveReviewCycle(UUID loanApplicationId);

    record ActiveReviewCycleSnapshot(
            UUID reviewCycleId,
            UUID assignedLoanOfficerUserId
    ) {
    }
}
