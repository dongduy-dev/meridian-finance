package com.meridian.platform.loan.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record CollateralLoanVerification(
        UUID id,
        UUID loanApplicationId,
        ProductVerificationResult productVerificationResult,
        LocalDateTime createdAt
) {

    public CollateralLoanVerification {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(productVerificationResult, "productVerificationResult must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (productVerificationResult != ProductVerificationResult.PENDING_MANUAL_REVIEW) {
            throw new IllegalArgumentException(
                    "Collateral Loan CP1 verification must remain pending manual review."
            );
        }
    }

    public static CollateralLoanVerification pendingManualReview(
            UUID id,
            LoanApplication loanApplication,
            LocalDateTime createdAt
    ) {
        Objects.requireNonNull(loanApplication, "loanApplication must not be null");
        if (loanApplication.productCode() != ProductCode.COLLATERAL_LOAN
                || loanApplication.productType() != ProductType.SECURED) {
            throw new IllegalArgumentException(
                    "Verification requires a Collateral Loan application."
            );
        }
        return new CollateralLoanVerification(
                id,
                loanApplication.id(),
                ProductVerificationResult.PENDING_MANUAL_REVIEW,
                createdAt
        );
    }
}
