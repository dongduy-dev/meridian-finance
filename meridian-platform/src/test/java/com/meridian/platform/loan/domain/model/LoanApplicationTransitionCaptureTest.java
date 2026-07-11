package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanApplicationTransitionCaptureTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);

    @Test
    void capturesSubmissionTransitionFact() {
        LoanApplicationTransitionResult result = LoanApplication.submittedWithTransition(
                UUID.randomUUID(),
                UUID.randomUUID(),
                product(),
                "SA-1",
                BigDecimal.ONE,
                1,
                NOW
        );

        assertTransition(result, null, LoanApplicationStatus.SUBMITTED, LoanApplicationTransitionAction.APPLICATION_SUBMITTED);
    }

    @Test
    void capturesReviewStartTransitionFact() {
        assertTransition(
                application(LoanApplicationStatus.SUBMITTED).startReviewWithTransition(),
                LoanApplicationStatus.SUBMITTED,
                LoanApplicationStatus.UNDER_REVIEW,
                LoanApplicationTransitionAction.REVIEW_STARTED
        );
    }

    @Test
    void capturesReviewRecommendationTransitionFacts() {
        assertTransition(
                application(LoanApplicationStatus.UNDER_REVIEW)
                        .applyReviewRecommendationWithTransition(LoanReviewRecommendationAction.RECOMMEND_APPROVAL),
                LoanApplicationStatus.UNDER_REVIEW,
                LoanApplicationStatus.APPROVAL_PENDING,
                LoanApplicationTransitionAction.RECOMMEND_APPROVAL
        );
        assertTransition(
                application(LoanApplicationStatus.UNDER_REVIEW)
                        .applyReviewRecommendationWithTransition(LoanReviewRecommendationAction.RECOMMEND_REJECTION),
                LoanApplicationStatus.UNDER_REVIEW,
                LoanApplicationStatus.APPROVAL_PENDING,
                LoanApplicationTransitionAction.RECOMMEND_REJECTION
        );
        assertTransition(
                application(LoanApplicationStatus.UNDER_REVIEW)
                        .applyReviewRecommendationWithTransition(LoanReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION),
                LoanApplicationStatus.UNDER_REVIEW,
                LoanApplicationStatus.RETURNED_FOR_REVISION,
                LoanApplicationTransitionAction.RETURN_TO_CUSTOMER_REVISION
        );
        assertTransition(
                application(LoanApplicationStatus.UNDER_REVIEW)
                        .applyReviewRecommendationWithTransition(LoanReviewRecommendationAction.REQUEST_STAFF_CORRECTION),
                LoanApplicationStatus.UNDER_REVIEW,
                LoanApplicationStatus.RETURNED_FOR_REVISION,
                LoanApplicationTransitionAction.REQUEST_STAFF_CORRECTION
        );
    }

    @Test
    void capturesApprovalDecisionTransitionFacts() {
        assertTransition(
                application(LoanApplicationStatus.APPROVAL_PENDING)
                        .applyApprovalDecisionWithTransition(LoanApprovalDecisionAction.APPROVE),
                LoanApplicationStatus.APPROVAL_PENDING,
                LoanApplicationStatus.APPROVED,
                LoanApplicationTransitionAction.APPROVE
        );
        assertTransition(
                application(LoanApplicationStatus.APPROVAL_PENDING)
                        .applyApprovalDecisionWithTransition(LoanApprovalDecisionAction.REJECT),
                LoanApplicationStatus.APPROVAL_PENDING,
                LoanApplicationStatus.REJECTED,
                LoanApplicationTransitionAction.REJECT
        );
        assertTransition(
                application(LoanApplicationStatus.APPROVAL_PENDING)
                        .applyApprovalDecisionWithTransition(LoanApprovalDecisionAction.RETURN_TO_LOAN_OFFICER_REVIEW),
                LoanApplicationStatus.APPROVAL_PENDING,
                LoanApplicationStatus.RETURNED_TO_REVIEW,
                LoanApplicationTransitionAction.RETURN_TO_LOAN_OFFICER_REVIEW
        );
        assertTransition(
                application(LoanApplicationStatus.APPROVAL_PENDING)
                        .applyApprovalDecisionWithTransition(LoanApprovalDecisionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION),
                LoanApplicationStatus.APPROVAL_PENDING,
                LoanApplicationStatus.RETURNED_FOR_REVISION,
                LoanApplicationTransitionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION
        );
    }

    @Test
    void capturesEachIntermediateTransitionForSalaryAdvanceApproval() {
        LoanApplication application = application(LoanApplicationStatus.APPROVAL_PENDING);

        LoanApplicationTransitionResult approved = application.applyApprovalDecisionWithTransition(
                LoanApprovalDecisionAction.APPROVE
        );
        LoanApplicationTransitionResult awaitingCustomer = approved.loanApplication().markCustomerAcceptancePendingWithTransition();

        assertTransition(
                approved,
                LoanApplicationStatus.APPROVAL_PENDING,
                LoanApplicationStatus.APPROVED,
                LoanApplicationTransitionAction.APPROVE
        );
        assertTransition(
                awaitingCustomer,
                LoanApplicationStatus.APPROVED,
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                LoanApplicationTransitionAction.APPROVED_OFFER_GENERATED
        );
    }

    @Test
    void capturesOfferResponseTransitionFacts() {
        assertTransition(
                application(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING).acceptApprovedOfferWithTransition(),
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                LoanApplicationStatus.CONTRACT_PENDING,
                LoanApplicationTransitionAction.OFFER_ACCEPTED
        );
        assertTransition(
                application(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING).declineApprovedOfferWithTransition(),
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                LoanApplicationStatus.CUSTOMER_DECLINED,
                LoanApplicationTransitionAction.OFFER_DECLINED
        );
        assertTransition(
                application(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING).expireApprovedOfferWithTransition(),
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                LoanApplicationStatus.EXPIRED,
                LoanApplicationTransitionAction.OFFER_EXPIRED
        );
    }

    @Test
    void idempotentTerminalOfferActionsProduceNoTransitionFact() {
        assertTrue(application(LoanApplicationStatus.CONTRACT_PENDING).acceptApprovedOfferWithTransition().transition().isEmpty());
        assertTrue(application(LoanApplicationStatus.CUSTOMER_DECLINED).declineApprovedOfferWithTransition().transition().isEmpty());
        assertTrue(application(LoanApplicationStatus.EXPIRED).expireApprovedOfferWithTransition().transition().isEmpty());
    }

    @Test
    void invalidTransitionsStillFailBeforeFactCreation() {
        assertThrows(BusinessStateConflictException.class,
                () -> application(LoanApplicationStatus.REJECTED).startReviewWithTransition());
        assertThrows(BusinessStateConflictException.class,
                () -> application(LoanApplicationStatus.SUBMITTED)
                        .applyReviewRecommendationWithTransition(LoanReviewRecommendationAction.RECOMMEND_APPROVAL));
        assertThrows(BusinessStateConflictException.class,
                () -> application(LoanApplicationStatus.UNDER_REVIEW)
                        .applyApprovalDecisionWithTransition(LoanApprovalDecisionAction.APPROVE));
    }

    private void assertTransition(
            LoanApplicationTransitionResult result,
            LoanApplicationStatus fromStatus,
            LoanApplicationStatus toStatus,
            LoanApplicationTransitionAction action
    ) {
        LoanApplicationTransitionFact fact = result.transition().orElseThrow();
        assertEquals(fromStatus, fact.fromStatus());
        assertEquals(toStatus, fact.toStatus());
        assertEquals(action, fact.action());
    }

    private LoanApplication application(LoanApplicationStatus status) {
        return new LoanApplication(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SA-1",
                ProductCode.SALARY_ADVANCE, ProductType.SALARY_BASED, status,
                BigDecimal.ONE, 1, NOW);
    }

    private LoanProduct product() {
        return new LoanProduct(
                UUID.randomUUID(),
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                "Salary Advance",
                null,
                true,
                BigDecimal.ONE,
                BigDecimal.TEN
        );
    }
}
