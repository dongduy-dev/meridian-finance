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
        String correctionReasonCode,
        String customerInstruction,
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
        correctionReasonCode = normalize(correctionReasonCode);
        customerInstruction = normalize(customerInstruction);
        if (outcome == DocumentReviewOutcome.REQUEST_REPLACEMENT) {
            if (!"DOCUMENT_REPLACEMENT_REQUIRED".equals(correctionReasonCode)
                    || customerInstruction == null
                    || customerInstruction.length() > 500
                    || hasDisallowedControl(customerInstruction)) {
                throw new BusinessRuleViolationException(
                        "INVALID_CORRECTION_PLAN",
                        "Replacement review requires a controlled reason and plain-text customer instruction."
                );
            }
        } else if (correctionReasonCode != null || customerInstruction != null) {
            throw new BusinessRuleViolationException(
                    "INVALID_CORRECTION_PLAN",
                    "Replacement correction fields are allowed only for REQUEST_REPLACEMENT."
            );
        }
        restrictedStaffNotes = normalizeNotes(restrictedStaffNotes);
        Objects.requireNonNull(reviewerUserId, "reviewerUserId must not be null");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
    }

    public DocumentReviewDecision(
            UUID id, UUID checklistItemId, UUID documentVersionId, UUID reviewRequestId,
            DocumentReviewOutcome outcome, DocumentWaiverReasonCode waiverReasonCode,
            String restrictedStaffNotes, UUID reviewerUserId, LocalDateTime decidedAt
    ) {
        this(id, checklistItemId, documentVersionId, reviewRequestId, outcome, waiverReasonCode,
                null, null, restrictedStaffNotes, reviewerUserId, decidedAt);
    }

    public boolean sameLogicalReview(
            UUID itemId,
            UUID versionId,
            DocumentReviewOutcome expectedOutcome,
            DocumentWaiverReasonCode expectedWaiverReason,
            String expectedCorrectionReason,
            String expectedCustomerInstruction,
            String expectedRestrictedStaffNotes,
            UUID reviewerId
    ) {
        return checklistItemId.equals(itemId)
                && documentVersionId.equals(versionId)
                && outcome == expectedOutcome
                && waiverReasonCode == expectedWaiverReason
                && Objects.equals(correctionReasonCode, normalize(expectedCorrectionReason))
                && Objects.equals(customerInstruction, normalize(expectedCustomerInstruction))
                && Objects.equals(restrictedStaffNotes, normalizeNotes(expectedRestrictedStaffNotes))
                && reviewerUserId.equals(reviewerId);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean hasDisallowedControl(String value) {
        return value.chars().anyMatch(character ->
                Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t');
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
