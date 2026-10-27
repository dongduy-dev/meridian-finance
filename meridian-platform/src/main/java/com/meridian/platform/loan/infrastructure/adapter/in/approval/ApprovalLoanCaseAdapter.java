package com.meridian.platform.loan.infrastructure.adapter.in.approval;

import com.meridian.platform.approval.application.spi.loan.ApprovalLoanCaseSpi;
import com.meridian.platform.loan.application.port.in.QueryApprovalLoanCaseUseCase;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ApprovalLoanCaseAdapter implements ApprovalLoanCaseSpi {
    private final QueryApprovalLoanCaseUseCase cases;

    public ApprovalLoanCaseAdapter(QueryApprovalLoanCaseUseCase cases) {
        this.cases = cases;
    }

    @Override
    public Optional<CaseSnapshot> findCase(UUID loanApplicationId) {
        return cases.findCase(loanApplicationId).map(value -> new CaseSnapshot(
                value.loanApplicationId(), value.customerId(), value.applicationNumber(),
                value.productCode(), value.productType(), value.requestedAmount(), value.requestedTermMonths(),
                value.applicationStatus(), value.submittedAt(), value.originationChannel(),
                new DocumentReadinessSnapshot(value.documentReadiness().uploadComplete(),
                        value.documentReadiness().processingReady()),
                new ProductReadinessSnapshot(value.productReadiness().productVerificationResult(),
                        value.productReadiness().readyForDecision()),
                value.currentReviewCycle() == null ? null : new ReviewCycleSnapshot(
                        value.currentReviewCycle().reviewCycleId(), value.currentReviewCycle().cycleNumber(),
                        value.currentReviewCycle().assignedLoanOfficerUserId(), value.currentReviewCycle().status(),
                        value.currentReviewCycle().startedAt(), value.currentReviewCycle().endedAt()),
                value.correctionOptions().stream().map(option -> new CorrectionOptionSnapshot(
                        option.documentType(), option.checklistItemId(), option.currentDocumentVersionId(),
                        option.allowedScopes())).toList()));
    }

    @Override
    public QueuePageSnapshot findDecisionQueue(String productCode, int page, int size) {
        var value = cases.findDecisionQueue(productCode, page, size);
        return new QueuePageSnapshot(value.page(), value.size(), value.totalElements(), value.totalPages(),
                value.items().stream().map(item -> new QueueItemSnapshot(
                        item.loanApplicationId(), item.applicationNumber(), item.productCode(), item.productType(),
                        item.requestedAmount(), item.requestedTermMonths(), item.applicationStatus(), item.submittedAt()
                )).toList());
    }
}
