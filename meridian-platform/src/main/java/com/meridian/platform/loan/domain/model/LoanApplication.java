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
        return submit(
                id,
                customerId,
                loanProduct,
                applicationNumber,
                requestedAmount,
                requestedTermMonths,
                submittedAt,
                LoanApplicationStatus.SUBMITTED
        );
    }

    public static LoanApplicationTransitionResult submit(
            UUID id,
            UUID customerId,
            LoanProduct loanProduct,
            String applicationNumber,
            BigDecimal requestedAmount,
            int requestedTermMonths,
            LocalDateTime submittedAt,
            LoanApplicationStatus initialStatus
    ) {
        Objects.requireNonNull(loanProduct, "loanProduct must not be null");
        Objects.requireNonNull(initialStatus, "initialStatus must not be null");
        if (initialStatus != LoanApplicationStatus.SUBMITTED
                && initialStatus != LoanApplicationStatus.DOCUMENTS_PENDING) {
            throw new IllegalArgumentException(
                    "Initial Loan Application status must be SUBMITTED or DOCUMENTS_PENDING."
            );
        }
        LoanApplication loanApplication = new LoanApplication(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(customerId, "customerId must not be null"),
                loanProduct.id(),
                Objects.requireNonNull(applicationNumber, "applicationNumber must not be null"),
                loanProduct.productCode(),
                loanProduct.productType(),
                initialStatus,
                Objects.requireNonNull(requestedAmount, "requestedAmount must not be null"),
                requestedTermMonths,
                Objects.requireNonNull(submittedAt, "submittedAt must not be null")
        );
        return LoanApplicationTransitionResult.of(
                loanApplication,
                new LoanApplicationTransitionFact(
                        loanApplication.id(),
                        null,
                        initialStatus,
                        LoanApplicationTransitionAction.SUBMIT_APPLICATION
                )
        );
    }

    public LoanApplicationTransitionResult completeDocumentUploads() {
        if (status != LoanApplicationStatus.DOCUMENTS_PENDING) {
            throw new BusinessStateConflictException(
                    "DOCUMENT_UPLOAD_COMPLETION_NOT_ALLOWED",
                    "Document uploads can only be completed while documents are pending."
            );
        }
        return transitionTo(LoanApplicationStatus.SUBMITTED, LoanApplicationTransitionAction.COMPLETE_DOCUMENT_UPLOADS);
    }

    public LoanApplicationTransitionResult startProductVerification() {
        if (status != LoanApplicationStatus.SUBMITTED) {
            throw new BusinessStateConflictException(
                    "PRODUCT_VERIFICATION_START_NOT_ALLOWED",
                    "Product verification can only start for a submitted Loan Application."
            );
        }
        return transitionTo(
                LoanApplicationStatus.VERIFICATION_PENDING,
                LoanApplicationTransitionAction.START_PRODUCT_VERIFICATION
        );
    }

    public LoanApplicationTransitionResult completeProductVerification() {
        return completeProductVerification(ProductVerificationResult.VERIFIED);
    }

    public LoanApplicationTransitionResult completeProductVerification(
            ProductVerificationResult verificationResult
    ) {
        Objects.requireNonNull(verificationResult, "verificationResult must not be null");
        if (status != LoanApplicationStatus.VERIFICATION_PENDING) {
            throw new BusinessStateConflictException(
                    "PRODUCT_VERIFICATION_COMPLETION_NOT_ALLOWED",
                    "Product verification can only complete while verification is pending."
            );
        }
        LoanApplicationStatus targetStatus = switch (verificationResult) {
            case VERIFIED -> LoanApplicationStatus.SUBMITTED;
            case FAILED -> LoanApplicationStatus.VERIFICATION_FAILED;
            case REQUIRES_MORE_INFORMATION -> LoanApplicationStatus.RETURNED_FOR_REVISION;
            case PENDING_MANUAL_REVIEW -> throw new IllegalArgumentException(
                    "Pending manual review is not a verification completion outcome."
            );
        };
        return transitionTo(
                targetStatus,
                LoanApplicationTransitionAction.COMPLETE_PRODUCT_VERIFICATION
        );
    }

    public LoanApplicationTransitionResult startReview() {
        if (status != LoanApplicationStatus.SUBMITTED) {
            throw new BusinessStateConflictException(
                    "LOAN_REVIEW_START_NOT_ALLOWED",
                    "Only submitted loan applications can start Loan Officer review."
            );
        }
        return transitionTo(LoanApplicationStatus.UNDER_REVIEW, LoanApplicationTransitionAction.START_REVIEW);
    }

    public LoanApplicationTransitionResult applyReviewRecommendation(LoanReviewRecommendationAction action) {
        Objects.requireNonNull(action, "action must not be null");
        if (!LOAN_OFFICER_RECOMMENDATION_SOURCE_STATUSES.contains(status)) {
            throw new BusinessStateConflictException(
                    "LOAN_RECOMMENDATION_NOT_ALLOWED",
                    "Loan Officer recommendation can only be recorded while the application is under review."
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
                    "APPROVAL_DECISION_NOT_ALLOWED",
                    "Approval decision can only be recorded while the application is pending approval."
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

    public LoanApplicationTransitionResult requestDocumentReplacementCorrection() {
        if (status != LoanApplicationStatus.SUBMITTED) {
            throw new BusinessStateConflictException(
                    "DOCUMENT_REPLACEMENT_NOT_ALLOWED",
                    "Pre-review document replacement can only be requested from submitted status."
            );
        }
        return transitionTo(
                LoanApplicationStatus.RETURNED_FOR_REVISION,
                LoanApplicationTransitionAction.RETURN_TO_CUSTOMER_REVISION
        );
    }

    public LoanApplicationTransitionResult resubmitCorrection(LoanApplicationStatus targetStatus) {
        if (status != LoanApplicationStatus.RETURNED_FOR_REVISION) {
            throw new BusinessStateConflictException(
                    "CORRECTION_RESUBMISSION_NOT_ALLOWED",
                    "Only an application returned for revision can be resubmitted."
            );
        }
        if (targetStatus != LoanApplicationStatus.SUBMITTED
                && targetStatus != LoanApplicationStatus.UNDER_REVIEW) {
            throw new IllegalArgumentException("Correction resubmission target is invalid.");
        }
        return transitionTo(targetStatus, LoanApplicationTransitionAction.RESUBMIT_CORRECTION);
    }

    public LoanApplicationTransitionResult cancelReturnedForRevision() {
        if (status != LoanApplicationStatus.RETURNED_FOR_REVISION) {
            throw new BusinessStateConflictException(
                    "LOAN_APPLICATION_CANCELLATION_NOT_ALLOWED",
                    "Only a Loan Application returned for revision can be cancelled by its Customer."
            );
        }
        return transitionTo(
                LoanApplicationStatus.CANCELLED,
                LoanApplicationTransitionAction.CANCEL_APPLICATION
        );
    }

    public LoanApplicationTransitionResult markCustomerAcceptancePending() {
        if (status != LoanApplicationStatus.APPROVED) {
            throw new BusinessStateConflictException(
                    "INVALID_APPLICATION_STATUS",
                    "Only approved loan applications can move to customer acceptance."
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
                    "OFFER_ACTION_CONFLICT",
                    "Approved offer cannot be accepted in the current application status."
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
                    "OFFER_ACTION_CONFLICT",
                    "Approved offer cannot be declined in the current application status."
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
                    "OFFER_ACTION_CONFLICT",
                    "Approved offer cannot be expired in the current application status."
            );
        }
        return transitionTo(
                LoanApplicationStatus.EXPIRED,
                LoanApplicationTransitionAction.EXPIRE_APPROVED_OFFER
        );
    }

    public LoanApplicationTransitionResult confirmDisbursementReadiness() {
        if (status != LoanApplicationStatus.CONTRACT_PENDING) {
            throw new BusinessStateConflictException(
                    "CONTRACT_READINESS_NOT_ALLOWED",
                    "Only a contract-pending loan application may be confirmed for disbursement."
            );
        }
        return transitionTo(
                LoanApplicationStatus.DISBURSEMENT_PENDING,
                LoanApplicationTransitionAction.CONFIRM_DISBURSEMENT_READINESS
        );
    }

    public LoanApplicationTransitionResult confirmManualDisbursement() {
        if (status != LoanApplicationStatus.DISBURSEMENT_PENDING) {
            throw new BusinessStateConflictException(
                    "MANUAL_DISBURSEMENT_CONFIRMATION_NOT_ALLOWED",
                    "Only a disbursement-pending loan application may be manually disbursed."
            );
        }
        return transitionTo(
                LoanApplicationStatus.DISBURSED,
                LoanApplicationTransitionAction.CONFIRM_MANUAL_DISBURSEMENT
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
