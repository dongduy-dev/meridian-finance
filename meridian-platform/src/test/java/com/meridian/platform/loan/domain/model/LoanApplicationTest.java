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
        LoanApplication result = loanApplication(LoanApplicationStatus.SUBMITTED).startReview();

        assertEquals(LoanApplicationStatus.UNDER_REVIEW, result.status());
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
        LoanApplication result = loanApplication(LoanApplicationStatus.UNDER_REVIEW)
                .applyReviewRecommendation(LoanReviewRecommendationAction.RECOMMEND_APPROVAL);

        assertEquals(LoanApplicationStatus.APPROVAL_PENDING, result.status());
    }

    @Test
    void recommendationForRejectionStillMovesApplicationToApprovalPending() {
        LoanApplication result = loanApplication(LoanApplicationStatus.UNDER_REVIEW)
                .applyReviewRecommendation(LoanReviewRecommendationAction.RECOMMEND_REJECTION);

        assertEquals(LoanApplicationStatus.APPROVAL_PENDING, result.status());
    }

    @Test
    void returnRecommendationMovesReturnedToReviewApplicationToRevision() {
        LoanApplication result = loanApplication(LoanApplicationStatus.RETURNED_TO_REVIEW)
                .applyReviewRecommendation(LoanReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION);

        assertEquals(LoanApplicationStatus.RETURNED_FOR_REVISION, result.status());
    }

    @Test
    void staffCorrectionRecommendationMovesApplicationToRevision() {
        LoanApplication result = loanApplication(LoanApplicationStatus.UNDER_REVIEW)
                .applyReviewRecommendation(LoanReviewRecommendationAction.REQUEST_STAFF_CORRECTION);

        assertEquals(LoanApplicationStatus.RETURNED_FOR_REVISION, result.status());
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
