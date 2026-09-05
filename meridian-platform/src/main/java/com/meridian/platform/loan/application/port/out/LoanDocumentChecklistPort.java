package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.document.domain.model.DocumentRequirementStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;

import java.util.List;
import java.util.UUID;

public interface LoanDocumentChecklistPort {

    SubmissionChecklistInitialState resolveSubmissionInitialState(ProductCode productCode);

    default List<SubmissionEvidenceRequirement> resolveSubmissionRequirements(ProductCode productCode) {
        return List.of();
    }

    SubmissionChecklistSnapshot createSubmissionChecklist(
            UUID loanApplicationId,
            ProductCode productCode,
            BusinessOperationContext operationContext
    );

    boolean isProcessingReady(UUID loanApplicationId);

    default ChecklistReadinessSnapshot readiness(UUID loanApplicationId) {
        return new ChecklistReadinessSnapshot(true, isProcessingReady(loanApplicationId));
    }

    default List<CurrentDocumentVersionTargetSnapshot> currentVersionTargets(UUID loanApplicationId) {
        throw new UnsupportedOperationException("Document correction targets are not supported.");
    }

    default UUID createRequiredItem(
            UUID loanApplicationId,
            DocumentType documentType,
            BusinessOperationContext operationContext
    ) {
        throw new UnsupportedOperationException("On-demand checklist items are not supported.");
    }

    default UUID requireCurrentVersion(UUID loanApplicationId, UUID checklistItemId) {
        throw new UnsupportedOperationException("Document version proof is not supported.");
    }

    default void requireCurrentVersion(
            UUID loanApplicationId, UUID checklistItemId, UUID expectedVersionId
    ) {
        throw new UnsupportedOperationException("Document version proof is not supported.");
    }

    default CurrentDocumentVersionSnapshot requireCurrentVersionSnapshot(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID expectedVersionId
    ) {
        throw new UnsupportedOperationException("Document version details are not supported.");
    }

    default boolean hasCurrentVersionDifferentFrom(UUID checklistItemId, UUID baselineVersionId) {
        throw new UnsupportedOperationException("Document replacement proof is not supported.");
    }

    default boolean isVersionAcceptedOrWaived(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID documentVersionId
    ) {
        throw new UnsupportedOperationException("Document review proof is not supported.");
    }

    default boolean isVersionReviewed(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID documentVersionId
    ) {
        throw new UnsupportedOperationException("Document review proof is not supported.");
    }

    record SubmissionChecklistInitialState(boolean uploadComplete) {
    }

    record SubmissionEvidenceRequirement(
            DocumentType documentType,
            DocumentRequirementStatus requirementStatus
    ) {
    }

    record SubmissionChecklistSnapshot(List<SubmissionChecklistItemSnapshot> items) {
        public SubmissionChecklistSnapshot {
            items = List.copyOf(items);
        }
    }

    record SubmissionChecklistItemSnapshot(
            UUID checklistItemId,
            DocumentType documentType,
            DocumentRequirementStatus requirementStatus
    ) {
    }

    record ChecklistReadinessSnapshot(boolean uploadComplete, boolean processingReady) {
    }

    record CurrentDocumentVersionSnapshot(
            DocumentType documentType,
            UUID documentVersionId
    ) {
    }

    record CurrentDocumentVersionTargetSnapshot(
            UUID checklistItemId,
            DocumentType documentType,
            DocumentRequirementStatus requirementStatus,
            UUID currentDocumentVersionId
    ) {
    }
}
