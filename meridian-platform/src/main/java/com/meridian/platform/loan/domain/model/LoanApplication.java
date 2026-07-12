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

    private static final Set<LoanApplicationStatus> LOAN_OFFICER_RECOMMENDATION_SOURCE_STATUSES = Set.of(
            LoanApplicationStatus.UNDER_REVIEW,
            LoanApplicationStatus.RETURNED_TO_REVIEW
    );

    public static LoanApplicationTransitionResult submit(
            UUID id,
            UUID customerId,
            LoanProduct loanProduct,
            String applicationNumber,
            BigDecimal requestedAmount,
            int requestedTermMonths,
            LocalDateTime submittedAt
    ) {
        Objects.requireNonNull(loanProduct, "loanProduct must not be null");
        LoanApplication loanApplication = new LoanApplication(
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
        return LoanApplicationTransitionResult.of(
                loanApplication,
                new LoanApplicationTransitionFact(
                        loanApplication.id(),
                        null,
                        LoanApplicationStatus.SUBMITTED,
                        LoanApplicationTransitionAction.SUBMIT_APPLICATION
                )
        );
    }

    public LoanApplicationTransitionResult startReview() {
        if (status != LoanApplicationStatus.SUBMITTED) {
            throw new BusinessStateConflictException(
                    "INVALID_APPLICATION_STATUS",
                    "Only submitted loan applications can start Loan Officer review."
            );
        }
        return transitionTo(LoanApplicationStatus.UNDER_REVIEW, LoanApplicationTransitionAction.START_REVIEW);
    }

    public LoanApplicationTransitionResult applyReviewRecommendation(LoanReviewRecommendationAction action) {
        Objects.requireNonNull(action, "action must not be null");
        if (!LOAN_OFFICER_RECOMMENDATION_SOURCE_STATUSES.contains(status)) {
            throw new BusinessStateConflictException(
                    "INVALID_APPLICATION_STATUS",
                    "Only applications under Loan Officer review can receive a recommendation."
            );
        }

        return switch (action) {
            case RECOMMEND_APPROVAL, RECOMMEND_REJECTION ->
                    transitionTo(
                            LoanApplicationStatus.APPROVAL_PENDING,
                            LoanApplicationTransitionAction.valueOf(action.name())
                    );
            case RETURN_TO_CUSTOMER_REVISION, REQUEST_STAFF_CORRECTION ->
                    transitionTo(
                            LoanApplicationStatus.RETURNED_FOR_REVISION,
                            LoanApplicationTransitionAction.valueOf(action.name())
                    );
        };
    }

    public LoanApplicationTransitionResult applyApprovalDecision(LoanApprovalDecisionAction action) {
        Objects.requireNonNull(action, "action must not be null");
        if (status != LoanApplicationStatus.APPROVAL_PENDING) {
            throw new BusinessStateConflictException(
                    "INVALID_APPLICATION_STATUS",
                    "Only applications pending approval can receive an approval decision."
            );
        }

        return switch (action) {
            case APPROVE -> transitionTo(LoanApplicationStatus.APPROVED, LoanApplicationTransitionAction.APPROVE);
            case REJECT -> transitionTo(LoanApplicationStatus.REJECTED, LoanApplicationTransitionAction.REJECT);
            case RETURN_TO_LOAN_OFFICER_REVIEW -> transitionTo(
                    LoanApplicationStatus.RETURNED_TO_REVIEW,
                    LoanApplicationTransitionAction.RETURN_TO_LOAN_OFFICER_REVIEW
            );
            case REQUEST_CUSTOMER_OR_STAFF_CORRECTION ->
                    transitionTo(
                            LoanApplicationStatus.RETURNED_FOR_REVISION,
                            LoanApplicationTransitionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION
                    );
        };
    }

    public LoanApplicationTransitionResult markCustomerAcceptancePending() {
        if (status != LoanApplicationStatus.APPROVED) {
            throw new BusinessStateConflictException(
                    "INVALID_APPLICATION_STATUS",
                    "Only approved applications can move to customer acceptance."
            );
        }
        return transitionTo(
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                LoanApplicationTransitionAction.GENERATE_APPROVED_OFFER
        );
    }

    public LoanApplicationTransitionResult acceptApprovedOffer() {
        if (status == LoanApplicationStatus.CONTRACT_PENDING) {
            return LoanApplicationTransitionResult.unchanged(this);
        }
        if (status != LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING) {
            throw new BusinessStateConflictException(
                    "INVALID_APPLICATION_STATUS",
                    "Only applications awaiting customer acceptance can accept an approved offer."
            );
        }
        return transitionTo(
                LoanApplicationStatus.CONTRACT_PENDING,
                LoanApplicationTransitionAction.ACCEPT_APPROVED_OFFER
        );
    }

    public LoanApplicationTransitionResult declineApprovedOffer() {
        if (status == LoanApplicationStatus.CUSTOMER_DECLINED) {
            return LoanApplicationTransitionResult.unchanged(this);
        }
        if (status != LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING) {
            throw new BusinessStateConflictException(
                    "INVALID_APPLICATION_STATUS",
                    "Only applications awaiting customer acceptance can decline an approved offer."
            );
        }
        return transitionTo(
                LoanApplicationStatus.CUSTOMER_DECLINED,
                LoanApplicationTransitionAction.DECLINE_APPROVED_OFFER
        );
    }

    public LoanApplicationTransitionResult expireApprovedOffer() {
        if (status == LoanApplicationStatus.EXPIRED) {
            return LoanApplicationTransitionResult.unchanged(this);
        }
        if (status != LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING) {
            throw new BusinessStateConflictException(
                    "INVALID_APPLICATION_STATUS",
                    "Only applications awaiting customer acceptance can expire an approved offer."
            );
        }
        return transitionTo(
                LoanApplicationStatus.EXPIRED,
                LoanApplicationTransitionAction.EXPIRE_APPROVED_OFFER
        );
    }

    private LoanApplicationTransitionResult transitionTo(
            LoanApplicationStatus nextStatus,
            LoanApplicationTransitionAction action
    ) {
        LoanApplication transitioned = withStatus(nextStatus);
        return LoanApplicationTransitionResult.of(
                transitioned,
                new LoanApplicationTransitionFact(id, status, nextStatus, action)
        );
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
