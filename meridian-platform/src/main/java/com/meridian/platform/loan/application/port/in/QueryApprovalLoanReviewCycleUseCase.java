package com.meridian.platform.loan.application.port.in;

import java.util.Optional;
import java.util.UUID;

public interface QueryApprovalLoanReviewCycleUseCase {
    Optional<ActiveReviewCycleSnapshot> findActiveReviewCycle(UUID loanApplicationId);

    record ActiveReviewCycleSnapshot(
            UUID reviewCycleId,
            UUID assignedLoanOfficerUserId
    ) {
    }
}
