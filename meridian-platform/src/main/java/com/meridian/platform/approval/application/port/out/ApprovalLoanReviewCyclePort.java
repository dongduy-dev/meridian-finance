package com.meridian.platform.approval.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface ApprovalLoanReviewCyclePort {
    Optional<UUID> findActiveReviewCycleId(UUID loanApplicationId);
}
