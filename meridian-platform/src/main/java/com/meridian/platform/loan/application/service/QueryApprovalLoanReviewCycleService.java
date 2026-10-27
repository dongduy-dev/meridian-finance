package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.QueryApprovalLoanReviewCycleUseCase;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class QueryApprovalLoanReviewCycleService implements QueryApprovalLoanReviewCycleUseCase {
    private final LoanReviewCycleRepository reviewCycleRepository;

    public QueryApprovalLoanReviewCycleService(LoanReviewCycleRepository reviewCycleRepository) {
        this.reviewCycleRepository = reviewCycleRepository;
    }

    @Override
    public Optional<ActiveReviewCycleSnapshot> findActiveReviewCycle(UUID loanApplicationId) {
        return reviewCycleRepository.findActiveByLoanApplicationId(loanApplicationId)
                .map(cycle -> new ActiveReviewCycleSnapshot(
                        cycle.id(), cycle.assignedLoanOfficerUserId()
                ));
    }
}
