package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.QueryApprovalLoanCaseUseCase.ReviewCycleSnapshot;
import com.meridian.platform.loan.application.port.in.QueryApprovalLoanReviewHistoryUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class QueryApprovalLoanReviewHistoryService implements QueryApprovalLoanReviewHistoryUseCase {
    private final LoanApplicationRepository applications;
    private final LoanReviewCycleRepository cycles;

    public QueryApprovalLoanReviewHistoryService(LoanApplicationRepository applications, LoanReviewCycleRepository cycles) {
        this.applications = applications;
        this.cycles = cycles;
    }

    @Override
    public Optional<HistorySnapshot> findHistory(UUID loanApplicationId) {
        return applications.findById(loanApplicationId).map(application -> new HistorySnapshot(
                application.id(), application.applicationNumber(), application.status().name(),
                cycles.findByLoanApplicationIdOrderByCycleNumberAsc(application.id()).stream()
                        .map(cycle -> new ReviewCycleSnapshot(cycle.id(), cycle.cycleNumber(),
                                cycle.assignedLoanOfficerUserId(), cycle.status().name(),
                                cycle.startedAt(), cycle.endedAt())).toList()
        ));
    }
}
