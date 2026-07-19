package com.meridian.platform.document.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record DocumentChecklistItem(
        UUID id,
        UUID checklistId,
        DocumentType documentType,
        DocumentRequirementStatus requirementStatus,
        UUID currentReviewDecisionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public DocumentChecklistItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(checklistId, "checklistId must not be null");
        Objects.requireNonNull(documentType, "documentType must not be null");
        Objects.requireNonNull(requirementStatus, "requirementStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public DocumentChecklistItem withCurrentReviewDecision(UUID reviewDecisionId, LocalDateTime changedAt) {
        return new DocumentChecklistItem(
                id,
                checklistId,
                documentType,
                requirementStatus,
                reviewDecisionId,
                createdAt,
                Objects.requireNonNull(changedAt, "changedAt must not be null")
        );
    }
}
