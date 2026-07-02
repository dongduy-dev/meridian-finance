package com.meridian.platform.approval.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record ApprovalDecision(
        UUID id,
        UUID loanApplicationId,
        UUID reviewRecommendationId,
        UUID approverUserId,
        ApprovalDecisionAction action,
        String reason,
        String internalNotes,
        LocalDateTime decidedAt
) {

    public static ApprovalDecision recorded(
            UUID id,
            UUID loanApplicationId,
            UUID reviewRecommendationId,
            UUID approverUserId,
            ApprovalDecisionAction action,
            String reason,
            String internalNotes,
            LocalDateTime decidedAt
    ) {
        Objects.requireNonNull(action, "action must not be null");
        String normalizedReason = normalizeOptionalText(reason);
        if (action.requiresReason() && normalizedReason == null) {
            throw new BusinessRuleViolationException(
                    "APPROVAL_DECISION_REASON_REQUIRED",
                    "A reason is required for this approval decision action."
            );
        }

        return new ApprovalDecision(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null"),
                Objects.requireNonNull(reviewRecommendationId, "reviewRecommendationId must not be null"),
                Objects.requireNonNull(approverUserId, "approverUserId must not be null"),
                action,
                normalizedReason,
                normalizeOptionalText(internalNotes),
                Objects.requireNonNull(decidedAt, "decidedAt must not be null")
        );
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}
