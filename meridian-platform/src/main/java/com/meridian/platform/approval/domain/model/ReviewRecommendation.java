package com.meridian.platform.approval.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record ReviewRecommendation(
        UUID id,
        UUID loanApplicationId,
        UUID reviewCycleId,
        UUID loanOfficerUserId,
        ReviewRecommendationAction action,
        String reason,
        CorrectionReasonCode reasonCode,
        String internalNotes,
        LocalDateTime submittedAt
) {

    public static ReviewRecommendation recorded(
            UUID id,
            UUID loanApplicationId,
            UUID reviewCycleId,
            UUID loanOfficerUserId,
            ReviewRecommendationAction action,
            String reason,
            CorrectionReasonCode reasonCode,
            String internalNotes,
            LocalDateTime submittedAt
    ) {
        Objects.requireNonNull(action, "action must not be null");
        String normalizedReason = normalizeOptionalText(reason);
        boolean revision = action == ReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION
                || action == ReviewRecommendationAction.REQUEST_STAFF_CORRECTION;
        if (revision && (normalizedReason != null || reasonCode == null)) {
            throw new BusinessRuleViolationException(
                    "INVALID_CORRECTION_PLAN",
                    "Revision recommendations require a controlled reason code and no free-text reason."
            );
        }
        if (!revision && reasonCode != null) {
            throw new BusinessRuleViolationException(
                    "INVALID_CORRECTION_PLAN",
                    "Non-revision recommendations cannot contain correction fields."
            );
        }
        if (!revision && action.requiresReason() && normalizedReason == null) {
            throw new BusinessRuleViolationException(
                    "RECOMMENDATION_REASON_REQUIRED",
                    "A reason is required for this review recommendation action."
            );
        }

        return new ReviewRecommendation(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null"),
                Objects.requireNonNull(reviewCycleId, "reviewCycleId must not be null"),
                Objects.requireNonNull(loanOfficerUserId, "loanOfficerUserId must not be null"),
                action,
                normalizedReason,
                reasonCode,
                normalizeOptionalText(internalNotes),
                Objects.requireNonNull(submittedAt, "submittedAt must not be null")
        );
    }

    public static ReviewRecommendation recorded(
            UUID id,
            UUID loanApplicationId,
            UUID loanOfficerUserId,
            ReviewRecommendationAction action,
            String reason,
            String internalNotes,
            LocalDateTime submittedAt
    ) {
        return recorded(id, loanApplicationId, UUID.randomUUID(), loanOfficerUserId,
                action, reason, null, internalNotes, submittedAt);
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}
