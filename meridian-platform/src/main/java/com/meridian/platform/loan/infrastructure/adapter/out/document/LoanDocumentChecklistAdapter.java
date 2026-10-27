package com.meridian.platform.loan.infrastructure.adapter.out.document;

import com.meridian.platform.document.application.port.in.DocumentChecklistUseCase;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.LoanDocumentType;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class LoanDocumentChecklistAdapter implements LoanDocumentChecklistPort {

    private final DocumentChecklistUseCase checklists;

    public LoanDocumentChecklistAdapter(DocumentChecklistUseCase checklists) {
        this.checklists = checklists;
    }

    @Override
    public SubmissionChecklistInitialState resolveSubmissionInitialState(ProductCode productCode) {
        var state = checklists.resolveSubmissionInitialState(submissionProduct(productCode));
        return new SubmissionChecklistInitialState(state.uploadComplete());
    }

    @Override
    public List<SubmissionEvidenceRequirement> resolveSubmissionRequirements(ProductCode productCode) {
        return checklists.resolveSubmissionRequirements(submissionProduct(productCode)).stream()
                .map(item -> new SubmissionEvidenceRequirement(
                        LoanDocumentType.valueOf(item.documentType()), RequirementStatus.valueOf(item.requirementStatus())))
                .toList();
    }

    @Override
    public SubmissionChecklistSnapshot createSubmissionChecklist(
            UUID loanApplicationId, ProductCode productCode, BusinessOperationContext operationContext
    ) {
        var checklist = checklists.createSubmissionChecklist(
                loanApplicationId, submissionProduct(productCode), operationContext);
        return new SubmissionChecklistSnapshot(checklist.items().stream()
                .map(item -> new SubmissionChecklistItemSnapshot(item.checklistItemId(),
                        LoanDocumentType.valueOf(item.documentType()), RequirementStatus.valueOf(item.requirementStatus())))
                .toList());
    }

    @Override
    public boolean isProcessingReady(UUID loanApplicationId) {
        return checklists.isProcessingReady(loanApplicationId);
    }

    @Override
    public ChecklistReadinessSnapshot readiness(UUID loanApplicationId) {
        var readiness = checklists.readiness(loanApplicationId);
        return new ChecklistReadinessSnapshot(readiness.uploadComplete(), readiness.processingReady());
    }

    @Override
    public List<CurrentDocumentVersionTargetSnapshot> currentVersionTargets(UUID loanApplicationId) {
        return checklists.currentVersionTargets(loanApplicationId).stream()
                .map(item -> new CurrentDocumentVersionTargetSnapshot(item.checklistItemId(),
                        LoanDocumentType.valueOf(item.documentType()), RequirementStatus.valueOf(item.requirementStatus()),
                        item.currentDocumentVersionId()))
                .toList();
    }

    @Override
    public UUID createRequiredItem(
            UUID loanApplicationId, LoanDocumentType documentType, BusinessOperationContext operationContext
    ) {
        return checklists.createRequiredItem(
                loanApplicationId, documentType == null ? null : documentType.name(), operationContext);
    }

    @Override
    public UUID requireCurrentVersion(UUID loanApplicationId, UUID checklistItemId) {
        return checklists.requireCurrentVersion(loanApplicationId, checklistItemId);
    }

    @Override
    public void requireCurrentVersion(UUID loanApplicationId, UUID checklistItemId, UUID expectedVersionId) {
        checklists.requireCurrentVersion(loanApplicationId, checklistItemId, expectedVersionId);
    }

    @Override
    public CurrentDocumentVersionSnapshot requireCurrentVersionSnapshot(
            UUID loanApplicationId, UUID checklistItemId, UUID expectedVersionId
    ) {
        var version = checklists.requireCurrentVersionSnapshot(loanApplicationId, checklistItemId, expectedVersionId);
        return new CurrentDocumentVersionSnapshot(LoanDocumentType.valueOf(version.documentType()), version.documentVersionId());
    }

    @Override
    public boolean hasCurrentVersionDifferentFrom(UUID checklistItemId, UUID baselineVersionId) {
        return checklists.hasCurrentVersionDifferentFrom(checklistItemId, baselineVersionId);
    }

    @Override
    public boolean isVersionAcceptedOrWaived(UUID loanApplicationId, UUID checklistItemId, UUID documentVersionId) {
        return checklists.isVersionAcceptedOrWaived(loanApplicationId, checklistItemId, documentVersionId);
    }

    @Override
    public boolean isVersionReviewed(UUID loanApplicationId, UUID checklistItemId, UUID documentVersionId) {
        return checklists.isVersionReviewed(loanApplicationId, checklistItemId, documentVersionId);
    }

    private static DocumentChecklistUseCase.SubmissionProduct submissionProduct(ProductCode productCode) {
        return productCode == null ? null : DocumentChecklistUseCase.SubmissionProduct.valueOf(productCode.name());
    }
}
