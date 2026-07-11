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

    public static LoanApplicationTransitionResult submittedWithTransition(
            UUID id,
            UUID customerId,
            LoanProduct loanProduct,
            String applicationNumber,
            BigDecimal requestedAmount,
            int requestedTermMonths,
            LocalDateTime submittedAt
    ) {
        Objects.requireNonNull(loanProduct, "loanProduct must not be null");

        LoanApplication application = new LoanApplication(
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
        return LoanApplicationTransitionResult.changed(application, new LoanApplicationTransitionFact(
                null, LoanApplicationStatus.SUBMITTED, LoanApplicationTransitionAction.APPLICATION_SUBMITTED
        ));
    }

    public LoanApplicationTransitionResult startReviewWithTransition() {
        if (status != LoanApplicationStatus.SUBMITTED) {
            throw new BusinessStateConflictException(
                    "LOAN_REVIEW_START_NOT_ALLOWED",
                    "Only submitted loan applications can start Loan Officer review."
            );
        }

        return withStatus(LoanApplicationStatus.UNDER_REVIEW, LoanApplicationTransitionAction.REVIEW_STARTED);
    }

    public LoanApplicationTransitionResult applyReviewRecommendationWithTransition(LoanReviewRecommendationAction action) {
        Objects.requireNonNull(action, "action must not be null");

        if (!REVIEW_RECOMMENDATION_SOURCE_STATUSES.contains(status)) {
            throw new BusinessStateConflictException(
                    "LOAN_RECOMMENDATION_NOT_ALLOWED",
                    "Loan Officer recommendation can only be recorded while the application is under review."
            );
        }

        return switch (action) {
            case RECOMMEND_APPROVAL -> withStatus(LoanApplicationStatus.APPROVAL_PENDING, LoanApplicationTransitionAction.RECOMMEND_APPROVAL);
            case RECOMMEND_REJECTION -> withStatus(LoanApplicationStatus.APPROVAL_PENDING, LoanApplicationTransitionAction.RECOMMEND_REJECTION);
            case RETURN_TO_CUSTOMER_REVISION -> withStatus(LoanApplicationStatus.RETURNED_FOR_REVISION, LoanApplicationTransitionAction.RETURN_TO_CUSTOMER_REVISION);
            case REQUEST_STAFF_CORRECTION -> withStatus(LoanApplicationStatus.RETURNED_FOR_REVISION, LoanApplicationTransitionAction.REQUEST_STAFF_CORRECTION);
        };
    }

    public LoanApplicationTransitionResult applyApprovalDecisionWithTransition(LoanApprovalDecisionAction action) {
        Objects.requireNonNull(action, "action must not be null");

        if (status != LoanApplicationStatus.APPROVAL_PENDING) {
            throw new BusinessStateConflictException(
                    "APPROVAL_DECISION_NOT_ALLOWED",
                    "Approval decision can only be recorded while the application is pending approval."
            );
        }

        return switch (action) {
            case APPROVE -> withStatus(LoanApplicationStatus.APPROVED, LoanApplicationTransitionAction.APPROVE);
            case REJECT -> withStatus(LoanApplicationStatus.REJECTED, LoanApplicationTransitionAction.REJECT);
            case RETURN_TO_LOAN_OFFICER_REVIEW -> withStatus(LoanApplicationStatus.RETURNED_TO_REVIEW, LoanApplicationTransitionAction.RETURN_TO_LOAN_OFFICER_REVIEW);
            case REQUEST_CUSTOMER_OR_STAFF_CORRECTION -> withStatus(LoanApplicationStatus.RETURNED_FOR_REVISION, LoanApplicationTransitionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION);
        };
    }

    public LoanApplicationTransitionResult markCustomerAcceptancePendingWithTransition() {
        if (status != LoanApplicationStatus.APPROVED) {
            throw new BusinessStateConflictException(
                    "INVALID_APPLICATION_STATUS",
                    "Only approved loan applications can move to customer acceptance."
            );
        }

        return withStatus(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING, LoanApplicationTransitionAction.APPROVED_OFFER_GENERATED);
    }

    public LoanApplicationTransitionResult acceptApprovedOfferWithTransition() {
        if (status == LoanApplicationStatus.CONTRACT_PENDING) {
            return LoanApplicationTransitionResult.unchanged(this);
        }
        if (status != LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING) {
            throw new BusinessStateConflictException(
                    "OFFER_ACTION_CONFLICT",
                    "Approved offer cannot be accepted in the current application status."
            );
        }

        return withStatus(LoanApplicationStatus.CONTRACT_PENDING, LoanApplicationTransitionAction.OFFER_ACCEPTED);
    }

    public LoanApplicationTransitionResult declineApprovedOfferWithTransition() {
        if (status == LoanApplicationStatus.CUSTOMER_DECLINED) {
            return LoanApplicationTransitionResult.unchanged(this);
        }
        if (status != LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING) {
            throw new BusinessStateConflictException(
                    "OFFER_ACTION_CONFLICT",
                    "Approved offer cannot be declined in the current application status."
            );
        }

        return withStatus(LoanApplicationStatus.CUSTOMER_DECLINED, LoanApplicationTransitionAction.OFFER_DECLINED);
    }

    public LoanApplicationTransitionResult expireApprovedOfferWithTransition() {
        if (status == LoanApplicationStatus.EXPIRED) {
            return LoanApplicationTransitionResult.unchanged(this);
        }
        if (status != LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING) {
            throw new BusinessStateConflictException(
                    "OFFER_ACTION_CONFLICT",
                    "Approved offer cannot be expired in the current application status."
            );
        }

        return withStatus(LoanApplicationStatus.EXPIRED, LoanApplicationTransitionAction.OFFER_EXPIRED);
    }

    private LoanApplicationTransitionResult withStatus(LoanApplicationStatus nextStatus, LoanApplicationTransitionAction action) {
        LoanApplication transitioned = new LoanApplication(
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
        return LoanApplicationTransitionResult.changed(
                transitioned,
                new LoanApplicationTransitionFact(status, nextStatus, action)
        );
    }
}