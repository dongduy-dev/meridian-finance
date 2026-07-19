package com.meridian.platform.document.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record DocumentReviewDecision(
        UUID id,
        UUID checklistItemId,
        UUID documentVersionId,
        UUID reviewRequestId,
        DocumentReviewOutcome outcome,
        DocumentWaiverReasonCode waiverReasonCode,
        String restrictedStaffNotes,
        UUID reviewerUserId,
        LocalDateTime decidedAt
) {
    public DocumentReviewDecision {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(checklistItemId, "checklistItemId must not be null");
        Objects.requireNonNull(documentVersionId, "documentVersionId must not be null");
        Objects.requireNonNull(reviewRequestId, "reviewRequestId must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (outcome == DocumentReviewOutcome.WAIVE_DOCUMENT && waiverReasonCode == null) {
            throw new BusinessRuleViolationException(
                    "DOCUMENT_WAIVER_REASON_REQUIRED",
                    "A controlled waiver reason code is required."
            );
        }
        if (outcome != DocumentReviewOutcome.WAIVE_DOCUMENT && waiverReasonCode != null) {
            throw new BusinessRuleViolationException(
                    "DOCUMENT_WAIVER_REASON_NOT_ALLOWED",
                    "A waiver reason code is only valid for a waiver."
            );
        }
        restrictedStaffNotes = normalizeNotes(restrictedStaffNotes);
        Objects.requireNonNull(reviewerUserId, "reviewerUserId must not be null");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
    }

    public boolean sameLogicalReview(
            UUID itemId,
            UUID versionId,
            DocumentReviewOutcome expectedOutcome,
            DocumentWaiverReasonCode expectedWaiverReason,
            UUID reviewerId
    ) {
        return checklistItemId.equals(itemId)
                && documentVersionId.equals(versionId)
                && outcome == expectedOutcome
                && waiverReasonCode == expectedWaiverReason
                && reviewerUserId.equals(reviewerId);
    }

    private static String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String normalized = notes.trim();
        if (normalized.length() > 2000 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessRuleViolationException(
                    "INVALID_DOCUMENT_REVIEW_NOTES",
                    "Restricted review notes are invalid."
            );
        }
        return normalized;
    }
}
