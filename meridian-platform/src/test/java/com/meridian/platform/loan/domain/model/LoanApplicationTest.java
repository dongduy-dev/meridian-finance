package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanApplicationTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void submissionCreatesInitialTransitionFact() {
        LoanApplicationTransitionResult result = LoanApplication.submit(
                LOAN_APPLICATION_ID,
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                loanProduct(),
                "SA-20260630-000001",
                BigDecimal.valueOf(3_000_000).setScale(2),
                1,
                LocalDateTime.now()
        );

        assertEquals(LoanApplicationStatus.SUBMITTED, result.loanApplication().status());
        assertTransition(
                result,
                null,
                LoanApplicationStatus.SUBMITTED,
                LoanApplicationTransitionAction.SUBMIT_APPLICATION
        );
    }

    @Test
    void startReviewMovesSubmittedApplicationUnderReview() {
        LoanApplicationTransitionResult result = loanApplication(LoanApplicationStatus.SUBMITTED).startReview();

        assertEquals(LoanApplicationStatus.UNDER_REVIEW, result.loanApplication().status());
        assertTransition(
                result,
                LoanApplicationStatus.SUBMITTED,
                LoanApplicationStatus.UNDER_REVIEW,
                LoanApplicationTransitionAction.START_REVIEW
        );
    }

    @Test
    void startsAndCompletesProductVerificationThroughCommonLifecycle() {
        LoanApplicationTransitionResult started = loanApplication(LoanApplicationStatus.SUBMITTED)
                .startProductVerification();

        assertEquals(LoanApplicationStatus.VERIFICATION_PENDING, started.loanApplication().status());
        assertTransition(
                started,
                LoanApplicationStatus.SUBMITTED,
                LoanApplicationStatus.VERIFICATION_PENDING,
                LoanApplicationTransitionAction.START_PRODUCT_VERIFICATION
        );

        LoanApplicationTransitionResult completed = started.loanApplication().completeProductVerification();
        assertEquals(LoanApplicationStatus.SUBMITTED, completed.loanApplication().status());
        assertTransition(
                completed,
                LoanApplicationStatus.VERIFICATION_PENDING,
                LoanApplicationStatus.SUBMITTED,
                LoanApplicationTransitionAction.COMPLETE_PRODUCT_VERIFICATION
        );
    }

    @Test
    void productVerificationTransitionsRejectWrongStatuses() {
        BusinessStateConflictException startFailure = assertThrows(
                BusinessStateConflictException.class,
                () -> loanApplication(LoanApplicationStatus.VERIFICATION_PENDING).startProductVerification()
        );
        BusinessStateConflictException completionFailure = assertThrows(
                BusinessStateConflictException.class,
                () -> loanApplication(LoanApplicationStatus.SUBMITTED).completeProductVerification()
        );

        assertEquals("PRODUCT_VERIFICATION_START_NOT_ALLOWED", startFailure.getErrorCode());
        assertEquals("PRODUCT_VERIFICATION_COMPLETION_NOT_ALLOWED", completionFailure.getErrorCode());
    }

    @Test
    void startReviewRejectsNonSubmittedApplication() {
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> loanApplication(LoanApplicationStatus.APPROVAL_PENDING).startReview()
        );

        assertEquals("LOAN_REVIEW_START_NOT_ALLOWED", exception.getErrorCode());
    }

    @Test
    void recommendationForApprovalMovesUnderReviewApplicationToApprovalPending() {
        LoanApplicationTransitionResult result = loanApplication(LoanApplicationStatus.UNDER_REVIEW)
                .applyReviewRecommendation(LoanReviewRecommendationAction.RECOMMEND_APPROVAL);

        assertEquals(LoanApplicationStatus.APPROVAL_PENDING, result.loanApplication().status());
        assertTransition(
                result,
                LoanApplicationStatus.UNDER_REVIEW,
                LoanApplicationStatus.APPROVAL_PENDING,
                LoanApplicationTransitionAction.RECOMMEND_APPROVAL
        );
    }

    @Test
    void recommendationForRejectionStillMovesApplicationToApprovalPending() {
        LoanApplicationTransitionResult result = loanApplication(LoanApplicationStatus.UNDER_REVIEW)
                .applyReviewRecommendation(LoanReviewRecommendationAction.RECOMMEND_REJECTION);

        assertEquals(LoanApplicationStatus.APPROVAL_PENDING, result.loanApplication().status());
        assertTransition(
                result,
                LoanApplicationStatus.UNDER_REVIEW,
                LoanApplicationStatus.APPROVAL_PENDING,
                LoanApplicationTransitionAction.RECOMMEND_REJECTION
        );
    }

    @Test
    void returnRecommendationMovesReturnedToReviewApplicationToRevision() {
        LoanApplicationTransitionResult result = loanApplication(LoanApplicationStatus.RETURNED_TO_REVIEW)
                .applyReviewRecommendation(LoanReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION);

        assertEquals(LoanApplicationStatus.RETURNED_FOR_REVISION, result.loanApplication().status());
        assertTransition(
                result,
                LoanApplicationStatus.RETURNED_TO_REVIEW,
                LoanApplicationStatus.RETURNED_FOR_REVISION,
                LoanApplicationTransitionAction.RETURN_TO_CUSTOMER_REVISION
        );
    }

    @Test
    void staffCorrectionRecommendationMovesApplicationToRevision() {
        LoanApplicationTransitionResult result = loanApplication(LoanApplicationStatus.UNDER_REVIEW)
                .applyReviewRecommendation(LoanReviewRecommendationAction.REQUEST_STAFF_CORRECTION);

        assertEquals(LoanApplicationStatus.RETURNED_FOR_REVISION, result.loanApplication().status());
        assertTransition(
                result,
                LoanApplicationStatus.UNDER_REVIEW,
                LoanApplicationStatus.RETURNED_FOR_REVISION,
                LoanApplicationTransitionAction.REQUEST_STAFF_CORRECTION
        );
    }

    @Test
    void recommendationRejectsSubmittedApplicationBeforeReviewStarts() {
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> loanApplication(LoanApplicationStatus.SUBMITTED)
                        .applyReviewRecommendation(LoanReviewRecommendationAction.RECOMMEND_APPROVAL)
        );

        assertEquals("LOAN_RECOMMENDATION_NOT_ALLOWED", exception.getErrorCode());
    }

    @Test
    void recommendationRejectsTerminalApplication() {
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> loanApplication(LoanApplicationStatus.REJECTED)
                        .applyReviewRecommendation(LoanReviewRecommendationAction.RECOMMEND_APPROVAL)
        );

        assertEquals("LOAN_RECOMMENDATION_NOT_ALLOWED", exception.getErrorCode());
    }

    @Test
    void approvalDecisionMovesApprovalPendingApplicationApproved() {
        LoanApplicationTransitionResult result = loanApplication(LoanApplicationStatus.APPROVAL_PENDING)
                .applyApprovalDecision(LoanApprovalDecisionAction.APPROVE);

        assertEquals(LoanApplicationStatus.APPROVED, result.loanApplication().status());
        assertTransition(
                result,
                LoanApplicationStatus.APPROVAL_PENDING,
                LoanApplicationStatus.APPROVED,
                LoanApplicationTransitionAction.APPROVE
        );
    }

    @Test
    void rejectionDecisionMovesApprovalPendingApplicationRejected() {
        LoanApplicationTransitionResult result = loanApplication(LoanApplicationStatus.APPROVAL_PENDING)
                .applyApprovalDecision(LoanApprovalDecisionAction.REJECT);

        assertEquals(LoanApplicationStatus.REJECTED, result.loanApplication().status());
        assertTransition(
                result,
                LoanApplicationStatus.APPROVAL_PENDING,
                LoanApplicationStatus.REJECTED,
                LoanApplicationTransitionAction.REJECT
        );
    }

    @Test
    void returnDecisionMovesApprovalPendingApplicationReturnedToReview() {
        LoanApplicationTransitionResult result = loanApplication(LoanApplicationStatus.APPROVAL_PENDING)
                .applyApprovalDecision(LoanApprovalDecisionAction.RETURN_TO_LOAN_OFFICER_REVIEW);

        assertEquals(LoanApplicationStatus.RETURNED_TO_REVIEW, result.loanApplication().status());
        assertTransition(
                result,
                LoanApplicationStatus.APPROVAL_PENDING,
                LoanApplicationStatus.RETURNED_TO_REVIEW,
                LoanApplicationTransitionAction.RETURN_TO_LOAN_OFFICER_REVIEW
        );
    }

    @Test
    void correctionDecisionMovesApprovalPendingApplicationReturnedForRevision() {
        LoanApplicationTransitionResult result = loanApplication(LoanApplicationStatus.APPROVAL_PENDING)
                .applyApprovalDecision(LoanApprovalDecisionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION);

        assertEquals(LoanApplicationStatus.RETURNED_FOR_REVISION, result.loanApplication().status());
        assertTransition(
                result,
                LoanApplicationStatus.APPROVAL_PENDING,
                LoanApplicationStatus.RETURNED_FOR_REVISION,
                LoanApplicationTransitionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION
        );
    }

    @Test
    void customerCancellationMovesOnlyReturnedApplicationToCancelled() {
        LoanApplicationTransitionResult result = loanApplication(
                LoanApplicationStatus.RETURNED_FOR_REVISION
        ).cancelReturnedForRevision();

        assertEquals(LoanApplicationStatus.CANCELLED, result.loanApplication().status());
        assertTransition(
                result,
                LoanApplicationStatus.RETURNED_FOR_REVISION,
                LoanApplicationStatus.CANCELLED,
                LoanApplicationTransitionAction.CANCEL_APPLICATION
        );
    }

    @Test
    void customerCancellationRejectsEveryOtherApplicationStatus() {
        for (LoanApplicationStatus status : LoanApplicationStatus.values()) {
            if (status == LoanApplicationStatus.RETURNED_FOR_REVISION) {
                continue;
            }
            BusinessStateConflictException exception = assertThrows(
                    BusinessStateConflictException.class,
                    () -> loanApplication(status).cancelReturnedForRevision()
            );
            assertEquals("LOAN_APPLICATION_CANCELLATION_NOT_ALLOWED", exception.getErrorCode());
        }
    }

    @Test
    void approvalDecisionRejectsNonApprovalPendingApplication() {
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> loanApplication(LoanApplicationStatus.UNDER_REVIEW)
                        .applyApprovalDecision(LoanApprovalDecisionAction.APPROVE)
        );

        assertEquals("APPROVAL_DECISION_NOT_ALLOWED", exception.getErrorCode());
    }

    @Test
    void approvedApplicationMovesToCustomerAcceptancePending() {
        LoanApplicationTransitionResult result = loanApplication(LoanApplicationStatus.APPROVED)
                .markCustomerAcceptancePending();

        assertEquals(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING, result.loanApplication().status());
        assertTransition(
                result,
                LoanApplicationStatus.APPROVED,
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                LoanApplicationTransitionAction.GENERATE_APPROVED_OFFER
        );
    }

    @Test
    void customerAcceptsPendingOfferAndMovesToContractPending() {
        LoanApplicationTransitionResult result = loanApplication(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING)
                .acceptApprovedOffer();

        assertEquals(LoanApplicationStatus.CONTRACT_PENDING, result.loanApplication().status());
        assertTransition(
                result,
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                LoanApplicationStatus.CONTRACT_PENDING,
                LoanApplicationTransitionAction.ACCEPT_APPROVED_OFFER
        );
    }

    @Test
    void repeatedAcceptedOfferProducesNoTransitionFact() {
        LoanApplicationTransitionResult result = loanApplication(LoanApplicationStatus.CONTRACT_PENDING)
                .acceptApprovedOffer();

        assertEquals(LoanApplicationStatus.CONTRACT_PENDING, result.loanApplication().status());
        assertTrue(result.facts().isEmpty());
    }

    @Test
    void customerDeclinesPendingOfferAndMovesToCustomerDeclined() {
        LoanApplicationTransitionResult result = loanApplication(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING)
                .declineApprovedOffer();

        assertEquals(LoanApplicationStatus.CUSTOMER_DECLINED, result.loanApplication().status());
        assertTransition(
                result,
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                LoanApplicationStatus.CUSTOMER_DECLINED,
                LoanApplicationTransitionAction.DECLINE_APPROVED_OFFER
        );
    }

    @Test
    void pendingOfferExpiresAndMovesApplicationExpired() {
        LoanApplicationTransitionResult result = loanApplication(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING)
                .expireApprovedOffer();

        assertEquals(LoanApplicationStatus.EXPIRED, result.loanApplication().status());
        assertTransition(
                result,
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                LoanApplicationStatus.EXPIRED,
                LoanApplicationTransitionAction.EXPIRE_APPROVED_OFFER
        );
    }

    @Test
    void offerActionRejectsContradictoryTerminalApplicationStatus() {
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> loanApplication(LoanApplicationStatus.CUSTOMER_DECLINED).acceptApprovedOffer()
        );

        assertEquals("OFFER_ACTION_CONFLICT", exception.getErrorCode());
    }

    private void assertTransition(
            LoanApplicationTransitionResult result,
            LoanApplicationStatus fromStatus,
            LoanApplicationStatus toStatus,
            LoanApplicationTransitionAction action
    ) {
        assertEquals(1, result.facts().size());
        LoanApplicationTransitionFact fact = result.facts().getFirst();
        assertEquals(LOAN_APPLICATION_ID, fact.loanApplicationId());
        if (fromStatus == null) {
            assertNull(fact.fromStatus());
        } else {
            assertEquals(fromStatus, fact.fromStatus());
        }
        assertEquals(toStatus, fact.toStatus());
        assertEquals(action, fact.action());
    }

    private LoanApplication loanApplication(LoanApplicationStatus status) {
        return new LoanApplication(
                LOAN_APPLICATION_ID,
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                "SA-20260630-000001",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                status,
                BigDecimal.valueOf(3_000_000).setScale(2),
                1,
                LocalDateTime.now()
        );
    }

    private LoanProduct loanProduct() {
        return new LoanProduct(
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                "Salary Advance",
                "Salary Advance",
                true,
                BigDecimal.valueOf(500_000).setScale(2),
                BigDecimal.valueOf(10_000_000).setScale(2)
        );
    }
}
