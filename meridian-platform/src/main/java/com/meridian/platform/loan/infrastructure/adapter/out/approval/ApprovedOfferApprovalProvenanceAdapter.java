package com.meridian.platform.loan.infrastructure.adapter.out.approval;

import com.meridian.platform.approval.application.port.in.QueryApprovedOfferApprovalProvenanceUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedOfferApprovalProvenancePort;
import com.meridian.platform.loan.application.port.out.ApprovedOfferApprovalSnapshot;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ApprovedOfferApprovalProvenanceAdapter implements ApprovedOfferApprovalProvenancePort {
    private final QueryApprovedOfferApprovalProvenanceUseCase approvals;

    public ApprovedOfferApprovalProvenanceAdapter(QueryApprovedOfferApprovalProvenanceUseCase approvals) {
        this.approvals = approvals;
    }

    @Override
    public ApprovedOfferApprovalSnapshot requireExactApproval(UUID loanApplicationId, LocalDateTime offerGeneratedAt) {
        var approval = approvals.requireExactApproval(loanApplicationId, offerGeneratedAt);
        return new ApprovedOfferApprovalSnapshot(approval.approverUserId(), approval.approvedAt());
    }
}
