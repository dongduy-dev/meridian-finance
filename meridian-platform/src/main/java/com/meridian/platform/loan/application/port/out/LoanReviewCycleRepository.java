package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;

import java.util.Optional;
import java.util.UUID;

public interface LoanReviewCycleRepository {
    LoanApplicationReviewCycle save(LoanApplicationReviewCycle cycle);

    Optional<LoanApplicationReviewCycle> findActiveByLoanApplicationId(UUID loanApplicationId);

    Optional<LoanApplicationReviewCycle> findActiveByLoanApplicationIdForUpdate(UUID loanApplicationId);

    Optional<LoanApplicationReviewCycle> findByIdForUpdate(UUID reviewCycleId);

    int nextCycleNumber(UUID loanApplicationId);
}
