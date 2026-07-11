package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoanApplicationTest {

    @Test
    void startReviewMovesSubmittedApplicationUnderReview() {
        LoanApplication result = loanApplication(LoanApplicationStatus.SUBMITTED).startReviewWithTransition().loanApplication();

        assertEquals(LoanApplicationStatus.UNDER_REVIEW, result.status());
    }

    @Test
    void startReviewRejectsNonSubmittedApplication() {
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> loanApplication(LoanApplicationStatus.APPROVAL_PENDING).startReviewWithTransition().loanApplication()
        );

        assertEquals("LOAN_REVIEW_START_NOT_ALLOWED", exception.getErrorCode());
    }

    @Test
    void recommendationForApprovalMovesUnderReviewApplicationToApprovalPending() {
        LoanApplication result = loanApplication(LoanApplicationStatus.UNDER_REVIEW)
                .applyReviewRecommendationWithTransition(LoanReviewRecommendationAction.RECOMMEND_APPROVAL).loanApplication();

        assertEquals(LoanApplicationStatus.APPROVAL_PENDING, result.status());
    }

    @Test
    void recommendationForRejectionStillMovesApplicationToApprovalPending() {
        LoanApplication result = loanApplication(LoanApplicationStatus.UNDER_REVIEW)
                .applyReviewRecommendationWithTransition(LoanReviewRecommendationAction.RECOMMEND_REJECTION).loanApplication();

        assertEquals(LoanApplicationStatus.APPROVAL_PENDING, result.status());
    }

    @Test
    void returnRecommendationMovesReturnedToReviewApplicationToRevision() {
        LoanApplication result = loanApplication(LoanApplicationStatus.RETURNED_TO_REVIEW)
                .applyReviewRecommendationWithTransition(LoanReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION).loanApplication();

        assertEquals(LoanApplicationStatus.RETURNED_FOR_REVISION, result.status());
    }

    @Test
    void staffCorrectionRecommendationMovesApplicationToRevision() {
        LoanApplication result = loanApplication(LoanApplicationStatus.UNDER_REVIEW)
                .applyReviewRecommendationWithTransition(LoanReviewRecommendationAction.REQUEST_STAFF_CORRECTION).loanApplication();

        assertEquals(LoanApplicationStatus.RETURNED_FOR_REVISION, result.status());
    }

    @Test
    void recommendationRejectsSubmittedApplicationBeforeReviewStarts() {
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> loanApplication(LoanApplicationStatus.SUBMITTED)
                        .applyReviewRecommendationWithTransition(LoanReviewRecommendationAction.RECOMMEND_APPROVAL)
        );

        assertEquals("LOAN_RECOMMENDATION_NOT_ALLOWED", exception.getErrorCode());
    }

    @Test
    void recommendationRejectsTerminalApplication() {
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> loanApplication(LoanApplicationStatus.REJECTED)
                        .applyReviewRecommendationWithTransition(LoanReviewRecommendationAction.RECOMMEND_APPROVAL)
        );

        assertEquals("LOAN_RECOMMENDATION_NOT_ALLOWED", exception.getErrorCode());
    }

    @Test
    void approvalDecisionMovesApprovalPendingApplicationApproved() {
        LoanApplication result = loanApplication(LoanApplicationStatus.APPROVAL_PENDING)
                .applyApprovalDecisionWithTransition(LoanApprovalDecisionAction.APPROVE).loanApplication();

        assertEquals(LoanApplicationStatus.APPROVED, result.status());
    }

    @Test
    void rejectionDecisionMovesApprovalPendingApplicationRejected() {
        LoanApplication result = loanApplication(LoanApplicationStatus.APPROVAL_PENDING)
                .applyApprovalDecisionWithTransition(LoanApprovalDecisionAction.REJECT).loanApplication();

        assertEquals(LoanApplicationStatus.REJECTED, result.status());
    }

    @Test
    void returnDecisionMovesApprovalPendingApplicationReturnedToReview() {
        LoanApplication result = loanApplication(LoanApplicationStatus.APPROVAL_PENDING)
                .applyApprovalDecisionWithTransition(LoanApprovalDecisionAction.RETURN_TO_LOAN_OFFICER_REVIEW).loanApplication();

        assertEquals(LoanApplicationStatus.RETURNED_TO_REVIEW, result.status());
    }

    @Test
    void correctionDecisionMovesApprovalPendingApplicationReturnedForRevision() {
        LoanApplication result = loanApplication(LoanApplicationStatus.APPROVAL_PENDING)
                .applyApprovalDecisionWithTransition(LoanApprovalDecisionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION).loanApplication();

        assertEquals(LoanApplicationStatus.RETURNED_FOR_REVISION, result.status());
    }

    @Test
    void approvalDecisionRejectsNonApprovalPendingApplication() {
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> loanApplication(LoanApplicationStatus.UNDER_REVIEW)
                        .applyApprovalDecisionWithTransition(LoanApprovalDecisionAction.APPROVE)
        );

        assertEquals("APPROVAL_DECISION_NOT_ALLOWED", exception.getErrorCode());
    }

    @Test
    void approvedApplicationMovesToCustomerAcceptancePending() {
        LoanApplication result = loanApplication(LoanApplicationStatus.APPROVED)
                .markCustomerAcceptancePendingWithTransition().loanApplication();

        assertEquals(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING, result.status());
    }

    @Test
    void customerAcceptsPendingOfferAndMovesToContractPending() {
        LoanApplication result = loanApplication(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING)
                .acceptApprovedOfferWithTransition().loanApplication();

        assertEquals(LoanApplicationStatus.CONTRACT_PENDING, result.status());
    }

    @Test
    void customerDeclinesPendingOfferAndMovesToCustomerDeclined() {
        LoanApplication result = loanApplication(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING)
                .declineApprovedOfferWithTransition().loanApplication();

        assertEquals(LoanApplicationStatus.CUSTOMER_DECLINED, result.status());
    }

    @Test
    void pendingOfferExpiresAndMovesApplicationExpired() {
        LoanApplication result = loanApplication(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING)
                .expireApprovedOfferWithTransition().loanApplication();

        assertEquals(LoanApplicationStatus.EXPIRED, result.status());
    }

    @Test
    void offerActionRejectsContradictoryTerminalApplicationStatus() {
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> loanApplication(LoanApplicationStatus.CUSTOMER_DECLINED).acceptApprovedOfferWithTransition().loanApplication()
        );

        assertEquals("OFFER_ACTION_CONFLICT", exception.getErrorCode());
    }
    private LoanApplication loanApplication(LoanApplicationStatus status) {
        return new LoanApplication(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
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
}
