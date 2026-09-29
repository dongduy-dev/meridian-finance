package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.port.in.ApprovedOfferApprovalProvenance;
import com.meridian.platform.approval.application.port.in.QueryApprovedOfferApprovalProvenanceUseCase;
import com.meridian.platform.approval.application.port.out.ApprovalDecisionRepository;
import com.meridian.platform.approval.domain.model.ApprovalDecisionAction;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class QueryApprovedOfferApprovalProvenanceService implements QueryApprovedOfferApprovalProvenanceUseCase {
    private final ApprovalDecisionRepository decisions;

    public QueryApprovedOfferApprovalProvenanceService(ApprovalDecisionRepository decisions) {
        this.decisions = decisions;
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovedOfferApprovalProvenance requireExactApproval(
            UUID loanApplicationId, LocalDateTime offerGeneratedAt
    ) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(offerGeneratedAt, "offerGeneratedAt must not be null");
        var matching = decisions.findByLoanApplicationIdOrderByDecidedAtDesc(loanApplicationId).stream()
                .filter(decision -> decision.loanApplicationId().equals(loanApplicationId)
                        && decision.action() == ApprovalDecisionAction.APPROVE
                        && decision.decidedAt().equals(offerGeneratedAt))
                .toList();
        if (matching.size() != 1) {
            throw new BusinessStateConflictException(
                    "SYSTEM_STATE_CONFLICT", "Approved-offer decision provenance is inconsistent.");
        }
        var decision = matching.getFirst();
        return new ApprovedOfferApprovalProvenance(decision.approverUserId(), decision.decidedAt());
    }
}
