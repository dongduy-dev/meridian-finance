package com.meridian.platform.loan.infrastructure.adapter.out.approval;

import com.meridian.platform.approval.application.port.out.ApprovalLoanCasePort.ReviewCycleSnapshot;
import com.meridian.platform.approval.application.port.out.ApprovalLoanReviewHistoryPort;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ApprovalLoanReviewHistoryAdapter implements ApprovalLoanReviewHistoryPort {
    private final LoanApplicationRepository applications;
    private final LoanReviewCycleRepository cycles;

    public ApprovalLoanReviewHistoryAdapter(LoanApplicationRepository applications, LoanReviewCycleRepository cycles) {
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
