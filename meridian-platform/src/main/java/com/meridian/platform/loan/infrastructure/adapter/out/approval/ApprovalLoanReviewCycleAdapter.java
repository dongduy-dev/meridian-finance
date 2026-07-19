package com.meridian.platform.loan.infrastructure.adapter.out.approval;

import com.meridian.platform.approval.application.port.out.ApprovalLoanReviewCyclePort;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ApprovalLoanReviewCycleAdapter implements ApprovalLoanReviewCyclePort {
    private final LoanReviewCycleRepository reviewCycleRepository;

    public ApprovalLoanReviewCycleAdapter(LoanReviewCycleRepository reviewCycleRepository) {
        this.reviewCycleRepository = reviewCycleRepository;
    }

    @Override
    public Optional<UUID> findActiveReviewCycleId(UUID loanApplicationId) {
        return reviewCycleRepository.findActiveByLoanApplicationId(loanApplicationId)
                .map(cycle -> cycle.id());
    }
}
