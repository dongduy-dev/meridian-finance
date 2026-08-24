package com.meridian.platform.loan.domain.model.collateral;

import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record CollateralLoanVerification(
        UUID id,
        UUID loanApplicationId,
        int verificationSequence,
        UUID sourceCorrectionRequestId,
        ProductVerificationResult productVerificationResult,
        LocalDateTime createdAt,
        UUID reviewedByUserId,
        LocalDateTime reviewedAt,
        String assessmentNote
) {

    public CollateralLoanVerification {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(productVerificationResult, "productVerificationResult must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (verificationSequence <= 0) {
            throw new IllegalArgumentException("verificationSequence must be positive");
        }
        if ((verificationSequence == 1) != (sourceCorrectionRequestId == null)) {
            throw new IllegalArgumentException(
                    "Only a later Collateral Loan verification sequence may reference its source correction."
            );
        }
        assessmentNote = normalizeOptionalText(assessmentNote);

        boolean evidenceEmpty = reviewedByUserId == null && reviewedAt == null && assessmentNote == null;
        boolean evidenceComplete = reviewedByUserId != null && reviewedAt != null && assessmentNote != null;
        if (!evidenceEmpty && !evidenceComplete) {
            throw new IllegalArgumentException(
                    "Collateral Loan verification review evidence must be either empty or complete."
            );
        }
        if (productVerificationResult == ProductVerificationResult.PENDING_MANUAL_REVIEW && !evidenceEmpty) {
            throw new IllegalArgumentException(
                    "Pending Collateral Loan verification cannot contain completed review evidence."
            );
        }
        if (productVerificationResult != ProductVerificationResult.PENDING_MANUAL_REVIEW
                && !evidenceComplete) {
            throw new IllegalArgumentException(
                    "Completed Collateral Loan verification requires reviewer, time, and assessment evidence."
            );
        }
        if (reviewedAt != null && reviewedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "Collateral Loan verification review time cannot precede creation time."
            );
        }
    }

    public CollateralLoanVerification(
            UUID id,
            UUID loanApplicationId,
            ProductVerificationResult productVerificationResult,
            LocalDateTime createdAt
    ) {
        this(
                id,
                loanApplicationId,
                1,
                null,
                productVerificationResult,
                createdAt,
                null,
                null,
                null
        );
    }

    public static CollateralLoanVerification pendingManualReview(
            UUID id,
            LoanApplication loanApplication,
            LocalDateTime createdAt
    ) {
        Objects.requireNonNull(loanApplication, "loanApplication must not be null");
        if (loanApplication.productCode() != ProductCode.COLLATERAL_LOAN
                || loanApplication.productType() != ProductType.SECURED) {
            throw new IllegalArgumentException("Verification requires a Collateral Loan application.");
        }
        return new CollateralLoanVerification(
                id,
                loanApplication.id(),
                1,
                null,
                ProductVerificationResult.PENDING_MANUAL_REVIEW,
                createdAt,
                null,
                null,
                null
        );
    }

    public static CollateralLoanVerification pendingReverification(
            UUID id,
            UUID loanApplicationId,
            int verificationSequence,
            UUID sourceCorrectionRequestId,
            LocalDateTime createdAt
    ) {
        return new CollateralLoanVerification(
                id,
                loanApplicationId,
                verificationSequence,
                Objects.requireNonNull(sourceCorrectionRequestId, "sourceCorrectionRequestId must not be null"),
                ProductVerificationResult.PENDING_MANUAL_REVIEW,
                createdAt,
                null,
                null,
                null
        );
    }

    public CollateralLoanVerification completeManualReview(
            CollateralLoanManualVerificationOutcome outcome,
            UUID reviewerUserId,
            LocalDateTime completedAt,
            String note
    ) {
        if (productVerificationResult != ProductVerificationResult.PENDING_MANUAL_REVIEW) {
            throw new BusinessStateConflictException(
                    "PRODUCT_VERIFICATION_NOT_PENDING",
                    "Collateral Loan verification is no longer pending manual review."
            );
        }
        String normalizedNote = normalizeOptionalText(note);
        if (normalizedNote == null || normalizedNote.length() > 2000) {
            throw new BusinessRuleViolationException(
                    "COLLATERAL_VERIFICATION_ASSESSMENT_REQUIRED",
                    "A manual verification assessment note between 1 and 2,000 characters is required."
            );
        }
        return new CollateralLoanVerification(
                id,
                loanApplicationId,
                verificationSequence,
                sourceCorrectionRequestId,
                ProductVerificationResult.valueOf(
                        Objects.requireNonNull(outcome, "outcome must not be null").name()
                ),
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
