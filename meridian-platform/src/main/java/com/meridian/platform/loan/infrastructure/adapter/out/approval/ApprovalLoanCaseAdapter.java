package com.meridian.platform.loan.infrastructure.adapter.out.approval;

import com.meridian.platform.approval.application.port.out.ApprovalLoanCasePort;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ApprovalLoanCaseAdapter implements ApprovalLoanCasePort {

    private final LoanApplicationRepository applications;
    private final SalaryAdvanceVerificationRepository salaryAdvanceVerifications;
    private final UnsecuredConsumerLoanVerificationRepository uclVerifications;
    private final CollateralLoanVerificationRepository collateralVerifications;
    private final LoanReviewCycleRepository reviewCycles;
    private final LoanDocumentChecklistPort documents;

    public ApprovalLoanCaseAdapter(
            LoanApplicationRepository applications,
            SalaryAdvanceVerificationRepository salaryAdvanceVerifications,
            UnsecuredConsumerLoanVerificationRepository uclVerifications,
            CollateralLoanVerificationRepository collateralVerifications,
            LoanReviewCycleRepository reviewCycles,
            LoanDocumentChecklistPort documents
    ) {
        this.applications = applications;
        this.salaryAdvanceVerifications = salaryAdvanceVerifications;
        this.uclVerifications = uclVerifications;
        this.collateralVerifications = collateralVerifications;
        this.reviewCycles = reviewCycles;
        this.documents = documents;
    }

    @Override
    public Optional<CaseSnapshot> findCase(UUID loanApplicationId) {
        return applications.findById(loanApplicationId).map(this::toCaseSnapshot);
    }

    @Override
    public QueuePageSnapshot findDecisionQueue(String productCode, int page, int size) {
        ProductCode selectedProduct = productCode == null ? null : ProductCode.valueOf(productCode);
        LoanApplicationRepository.StaffPage selected = applications.findStaffPage(
                selectedProduct,
                LoanApplicationStatus.APPROVAL_PENDING,
                page,
                size
        );
        return new QueuePageSnapshot(
                selected.page(),
                selected.size(),
                selected.totalElements(),
                selected.totalPages(),
                selected.applications().stream().map(this::toQueueItem).toList()
        );
    }

    private CaseSnapshot toCaseSnapshot(LoanApplication application) {
        LoanDocumentChecklistPort.ChecklistReadinessSnapshot documentReadiness =
                documents.readiness(application.id());
        ProductVerificationResult productVerification = verificationResult(application);
        LoanApplicationReviewCycle currentCycle = reviewCycles
                .findLatestByLoanApplicationId(application.id())
                .orElse(null);
        return new CaseSnapshot(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                application.status().name(),
                application.submittedAt(),
                new DocumentReadinessSnapshot(
                        documentReadiness.uploadComplete(),
                        documentReadiness.processingReady()
                ),
                new ProductReadinessSnapshot(
                        productVerification.name(),
                        productVerification == ProductVerificationResult.VERIFIED
                                && documentReadiness.processingReady()
                ),
                currentCycle == null ? null : new ReviewCycleSnapshot(
                        currentCycle.id(),
                        currentCycle.cycleNumber(),
                        currentCycle.status().name(),
                        currentCycle.startedAt(),
                        currentCycle.endedAt()
                ),
                correctionOptions(application)
        );
    }

    private List<CorrectionOptionSnapshot> correctionOptions(LoanApplication application) {
        List<CorrectionOptionSnapshot> options = new ArrayList<>();
        List<LoanDocumentChecklistPort.CurrentDocumentVersionTargetSnapshot> currentTargets =
                documents.currentVersionTargets(application.id());
        currentTargets.stream()
                .filter(target -> isProductCorrectionDocument(application.productCode(), target.documentType()))
                .forEach(target -> options.add(new CorrectionOptionSnapshot(
                        target.documentType().name(),
                        target.checklistItemId(),
                        target.currentDocumentVersionId(),
                        List.of("DOCUMENT_REPLACEMENT", "DOCUMENT_REVIEW")
                )));

        if (application.productCode() == ProductCode.SALARY_ADVANCE
                && currentTargets.stream().noneMatch(target -> target.documentType() == DocumentType.RECENT_PAYSLIP)) {
            options.add(new CorrectionOptionSnapshot(
                    DocumentType.RECENT_PAYSLIP.name(),
                    null,
                    null,
                    List.of("SUPPORTING_DOCUMENT_UPLOAD")
            ));
        }
        return options;
    }

    private boolean isProductCorrectionDocument(ProductCode productCode, DocumentType documentType) {
        return switch (productCode) {
            case SALARY_ADVANCE -> documentType == DocumentType.RECENT_PAYSLIP;
            case UNSECURED_CONSUMER_LOAN -> documentType == DocumentType.INCOME_PROOF
                    || documentType == DocumentType.BANK_STATEMENT
                    || documentType == DocumentType.EMPLOYMENT_PROOF;
            case COLLATERAL_LOAN -> documentType == DocumentType.COLLATERAL_OWNERSHIP_EVIDENCE;
        };
    }

    private ProductVerificationResult verificationResult(LoanApplication application) {
        return switch (application.productCode()) {
            case SALARY_ADVANCE -> salaryAdvanceVerifications.findByLoanApplicationId(application.id())
                    .orElseThrow(ApprovalLoanCaseAdapter::systemConflict)
                    .productVerificationResult();
            case UNSECURED_CONSUMER_LOAN -> uclVerifications
                    .findLatestByLoanApplicationId(application.id())
                    .orElseThrow(ApprovalLoanCaseAdapter::systemConflict)
                    .productVerificationResult();
            case COLLATERAL_LOAN -> collateralVerifications
                    .findLatestByLoanApplicationId(application.id())
                    .orElseThrow(ApprovalLoanCaseAdapter::systemConflict)
                    .productVerificationResult();
        };
    }

    private QueueItemSnapshot toQueueItem(LoanApplication application) {
        return new QueueItemSnapshot(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                application.status().name(),
                application.submittedAt()
        );
    }

    private static BusinessStateConflictException systemConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Authoritative product verification evidence is inconsistent."
        );
    }
}
