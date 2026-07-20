package com.meridian.platform.document.domain.model;

import java.util.UUID;

public record DocumentChecklistItemState(
        UUID checklistItemId,
        DocumentRequirementStatus requirementStatus,
        UUID currentDocumentVersionId,
        DocumentReviewOutcome currentReviewOutcome
) {
    public boolean uploadComplete() {
        if (requirementStatus != DocumentRequirementStatus.REQUIRED) {
            return true;
        }
        if (currentReviewOutcome == DocumentReviewOutcome.WAIVE_DOCUMENT
                || currentReviewOutcome == DocumentReviewOutcome.ACCEPT_DOCUMENT) {
            return true;
        }
        return currentDocumentVersionId != null
                && currentReviewOutcome != DocumentReviewOutcome.REQUEST_REPLACEMENT;
    }

    public boolean processingReady() {
        if (requirementStatus != DocumentRequirementStatus.REQUIRED) {
            return true;
        }
        return currentReviewOutcome == DocumentReviewOutcome.ACCEPT_DOCUMENT
                || currentReviewOutcome == DocumentReviewOutcome.WAIVE_DOCUMENT;
    }
}
