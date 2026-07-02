package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record LoanApplication(
        UUID id,
        UUID customerId,
        UUID loanProductId,
        String applicationNumber,
        ProductCode productCode,
        ProductType productType,
        LoanApplicationStatus status,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        LocalDateTime submittedAt
) {

    private static final Set<LoanApplicationStatus> REVIEW_RECOMMENDATION_SOURCE_STATUSES = Set.of(
            LoanApplicationStatus.UNDER_REVIEW,
            LoanApplicationStatus.RETURNED_TO_REVIEW
    );

    public static LoanApplication submitted(
            UUID id,
            UUID customerId,
            LoanProduct loanProduct,
            String applicationNumber,
            BigDecimal requestedAmount,
            int requestedTermMonths,
            LocalDateTime submittedAt
    ) {
        Objects.requireNonNull(loanProduct, "loanProduct must not be null");

        return new LoanApplication(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(customerId, "customerId must not be null"),
                loanProduct.id(),
                Objects.requireNonNull(applicationNumber, "applicationNumber must not be null"),
                loanProduct.productCode(),
                loanProduct.productType(),
                LoanApplicationStatus.SUBMITTED,
                Objects.requireNonNull(requestedAmount, "requestedAmount must not be null"),
                requestedTermMonths,
                Objects.requireNonNull(submittedAt, "submittedAt must not be null")
        );
    }

    public LoanApplication startReview() {
        if (status != LoanApplicationStatus.SUBMITTED) {
            throw new BusinessStateConflictException(
                    "LOAN_REVIEW_START_NOT_ALLOWED",
                    "Only submitted loan applications can start Loan Officer review."
            );
        }

        return withStatus(LoanApplicationStatus.UNDER_REVIEW);
    }

    public LoanApplication applyReviewRecommendation(LoanReviewRecommendationAction action) {
        Objects.requireNonNull(action, "action must not be null");

        if (!REVIEW_RECOMMENDATION_SOURCE_STATUSES.contains(status)) {
            throw new BusinessStateConflictException(
                    "LOAN_RECOMMENDATION_NOT_ALLOWED",
                    "Loan Officer recommendation can only be recorded while the application is under review."
            );
        }

        return switch (action) {
            case RECOMMEND_APPROVAL, RECOMMEND_REJECTION -> withStatus(LoanApplicationStatus.APPROVAL_PENDING);
            case RETURN_TO_CUSTOMER_REVISION, REQUEST_STAFF_CORRECTION ->
                    withStatus(LoanApplicationStatus.RETURNED_FOR_REVISION);
        };
    }

    public LoanApplication applyApprovalDecision(LoanApprovalDecisionAction action) {
        Objects.requireNonNull(action, "action must not be null");

        if (status != LoanApplicationStatus.APPROVAL_PENDING) {
            throw new BusinessStateConflictException(
                    "APPROVAL_DECISION_NOT_ALLOWED",
                    "Approval decision can only be recorded while the application is pending approval."
            );
        }

        return switch (action) {
            case APPROVE -> withStatus(LoanApplicationStatus.APPROVED);
            case REJECT -> withStatus(LoanApplicationStatus.REJECTED);
            case RETURN_TO_LOAN_OFFICER_REVIEW -> withStatus(LoanApplicationStatus.RETURNED_TO_REVIEW);
            case REQUEST_CUSTOMER_OR_STAFF_CORRECTION -> withStatus(LoanApplicationStatus.RETURNED_FOR_REVISION);
        };
    }

    private LoanApplication withStatus(LoanApplicationStatus nextStatus) {
        return new LoanApplication(
                id,
                customerId,
                loanProductId,
                applicationNumber,
                productCode,
                productType,
                nextStatus,
                requestedAmount,
                requestedTermMonths,
                submittedAt
        );
    }
}
