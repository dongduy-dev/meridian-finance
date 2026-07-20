package com.meridian.platform.document.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentChecklistReadinessTest {

    @Test
    void emptyChecklistIsUploadCompleteAndProcessingReady() {
        DocumentChecklistReadiness readiness = DocumentChecklistReadiness.empty();

        assertTrue(readiness.uploadComplete());
        assertTrue(readiness.processingReady());
    }

    @Test
    void requiredUploadCanBeCompleteWhileManualReviewIsPending() {
        DocumentChecklistReadiness readiness = DocumentChecklistReadiness.from(List.of(state(
                DocumentRequirementStatus.REQUIRED,
                UUID.randomUUID(),
                null
        )));

        assertTrue(readiness.uploadComplete());
        assertFalse(readiness.processingReady());
    }

    @Test
    void replacementRequirementInvalidatesBothReadinessLevels() {
        DocumentChecklistReadiness readiness = DocumentChecklistReadiness.from(List.of(state(
                DocumentRequirementStatus.REQUIRED,
                UUID.randomUUID(),
                DocumentReviewOutcome.REQUEST_REPLACEMENT
        )));

        assertFalse(readiness.uploadComplete());
        assertFalse(readiness.processingReady());
    }

    @Test
    void acceptAndWaiveMakeRequiredItemsProcessingReady() {
        assertTrue(DocumentChecklistReadiness.from(List.of(state(
                DocumentRequirementStatus.REQUIRED,
                UUID.randomUUID(),
                DocumentReviewOutcome.ACCEPT_DOCUMENT
        ))).processingReady());
        assertTrue(DocumentChecklistReadiness.from(List.of(state(
                DocumentRequirementStatus.REQUIRED,
                UUID.randomUUID(),
                DocumentReviewOutcome.WAIVE_DOCUMENT
        ))).processingReady());
    }

    @Test
    void optionalAndNotRequiredItemsDoNotBlockReadiness() {
        DocumentChecklistReadiness readiness = DocumentChecklistReadiness.from(List.of(
                state(DocumentRequirementStatus.OPTIONAL, null, null),
                state(DocumentRequirementStatus.NOT_REQUIRED, null, null)
        ));

        assertTrue(readiness.uploadComplete());
        assertTrue(readiness.processingReady());
    }

    @Test
    void rejectDocumentIsNotAnExecutableOutcome() {
        assertThrows(IllegalArgumentException.class, () -> DocumentReviewOutcome.valueOf("REJECT_DOCUMENT"));
    }

    private DocumentChecklistItemState state(
            DocumentRequirementStatus requirementStatus,
            UUID versionId,
            DocumentReviewOutcome outcome
    ) {
        return new DocumentChecklistItemState(UUID.randomUUID(), requirementStatus, versionId, outcome);
    }
}
