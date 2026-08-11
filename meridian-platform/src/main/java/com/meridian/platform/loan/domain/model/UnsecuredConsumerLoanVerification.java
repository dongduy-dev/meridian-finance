package com.meridian.platform.loan.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record UnsecuredConsumerLoanVerification(
        UUID id,
        UUID loanApplicationId,
        ProductVerificationResult productVerificationResult,
        LocalDateTime createdAt
) {

    public UnsecuredConsumerLoanVerification {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(productVerificationResult, "productVerificationResult must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
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
                createdAt
        );
    }
}
