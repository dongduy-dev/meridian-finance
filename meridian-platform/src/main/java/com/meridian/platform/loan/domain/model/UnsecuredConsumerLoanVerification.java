package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record UnsecuredConsumerLoanVerification(
        UUID id,
        UUID loanApplicationId,
        ProductVerificationResult productVerificationResult,
        LocalDateTime createdAt,
        UUID reviewedByUserId,
        LocalDateTime reviewedAt,
        String assessmentNote
) {

    public UnsecuredConsumerLoanVerification {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(productVerificationResult, "productVerificationResult must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        assessmentNote = normalizeOptionalText(assessmentNote);

        boolean evidenceEmpty = reviewedByUserId == null && reviewedAt == null && assessmentNote == null;
        boolean evidenceComplete = reviewedByUserId != null && reviewedAt != null && assessmentNote != null;
        if (!evidenceEmpty && !evidenceComplete) {
            throw new IllegalArgumentException(
                    "UCL verification review evidence must be either empty or complete."
            );
        }
        if (productVerificationResult == ProductVerificationResult.PENDING_MANUAL_REVIEW && !evidenceEmpty) {
            throw new IllegalArgumentException(
                    "Pending UCL verification cannot contain completed review evidence."
            );
        }
        if (productVerificationResult == ProductVerificationResult.VERIFIED && !evidenceComplete) {
            throw new IllegalArgumentException(
                    "Verified UCL verification requires reviewer, time, and assessment evidence."
            );
        }
        if (reviewedAt != null && reviewedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("UCL verification review time cannot precede creation time.");
        }
    }

    public static UnsecuredConsumerLoanVerification pendingManualReview(
            UUID id,
            LoanApplication loanApplication,
            LocalDateTime createdAt
    ) {
        Objects.requireNonNull(loanApplication, "loanApplication must not be null");
        if (loanApplication.productCode() != ProductCode.UNSECURED_CONSUMER_LOAN
                || loanApplication.productType() != ProductType.UNSECURED) {
            throw new IllegalArgumentException("Verification requires an Unsecured Consumer Loan application.");
        }
        return new UnsecuredConsumerLoanVerification(
                id,
                loanApplication.id(),
                ProductVerificationResult.PENDING_MANUAL_REVIEW,
                createdAt,
                null,
                null,
                null
        );
    }

    public UnsecuredConsumerLoanVerification completeManualReview(
            UUID reviewerUserId,
            LocalDateTime completedAt,
            String note
    ) {
        if (productVerificationResult != ProductVerificationResult.PENDING_MANUAL_REVIEW) {
            throw new BusinessStateConflictException(
                    "PRODUCT_VERIFICATION_NOT_PENDING",
                    "Unsecured Consumer Loan verification is no longer pending manual review."
            );
        }
        String normalizedNote = normalizeOptionalText(note);
        if (normalizedNote == null) {
            throw new BusinessRuleViolationException(
                    "UCL_VERIFICATION_ASSESSMENT_REQUIRED",
                    "A manual verification assessment note is required."
            );
        }
        return new UnsecuredConsumerLoanVerification(
                id,
                loanApplicationId,
                ProductVerificationResult.VERIFIED,
                createdAt,
                Objects.requireNonNull(reviewerUserId, "reviewerUserId must not be null"),
                Objects.requireNonNull(completedAt, "completedAt must not be null"),
                normalizedNote
        );
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
