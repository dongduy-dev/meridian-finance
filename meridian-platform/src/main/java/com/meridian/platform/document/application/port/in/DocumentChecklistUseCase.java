package com.meridian.platform.document.application.port.in;

import com.meridian.platform.shared.application.operation.BusinessOperationContext;

import java.util.List;
import java.util.UUID;

/** Purpose-limited checklist facts and evidence proofs owned by Document. */
public interface DocumentChecklistUseCase {

    SubmissionChecklistInitialState resolveSubmissionInitialState(SubmissionProduct productCode);

    List<SubmissionEvidenceRequirement> resolveSubmissionRequirements(SubmissionProduct productCode);

    SubmissionChecklistSnapshot createSubmissionChecklist(
            UUID loanApplicationId, SubmissionProduct productCode, BusinessOperationContext operationContext);

    boolean isProcessingReady(UUID loanApplicationId);

    ChecklistReadinessSnapshot readiness(UUID loanApplicationId);

    List<CurrentDocumentVersionTargetSnapshot> currentVersionTargets(UUID loanApplicationId);

    UUID createRequiredItem(UUID loanApplicationId, String documentType, BusinessOperationContext operationContext);

    UUID requireCurrentVersion(UUID loanApplicationId, UUID checklistItemId);

    void requireCurrentVersion(UUID loanApplicationId, UUID checklistItemId, UUID expectedVersionId);

    CurrentDocumentVersionSnapshot requireCurrentVersionSnapshot(
            UUID loanApplicationId, UUID checklistItemId, UUID expectedVersionId);

    boolean hasCurrentVersionDifferentFrom(UUID checklistItemId, UUID baselineVersionId);

    boolean isVersionAcceptedOrWaived(UUID loanApplicationId, UUID checklistItemId, UUID documentVersionId);

    boolean isVersionReviewed(UUID loanApplicationId, UUID checklistItemId, UUID documentVersionId);

    enum SubmissionProduct {
        SALARY_ADVANCE,
        UNSECURED_CONSUMER_LOAN,
        COLLATERAL_LOAN
    }

    record SubmissionChecklistInitialState(boolean uploadComplete) { }

    record SubmissionEvidenceRequirement(String documentType, String requirementStatus) { }

    record SubmissionChecklistSnapshot(List<SubmissionChecklistItemSnapshot> items) {
        public SubmissionChecklistSnapshot {
            items = List.copyOf(items);
        }
    }

    record SubmissionChecklistItemSnapshot(UUID checklistItemId, String documentType, String requirementStatus) { }

    record ChecklistReadinessSnapshot(boolean uploadComplete, boolean processingReady) { }

    record CurrentDocumentVersionSnapshot(String documentType, UUID documentVersionId) { }

    record CurrentDocumentVersionTargetSnapshot(
            UUID checklistItemId, String documentType, String requirementStatus, UUID currentDocumentVersionId) { }
}
