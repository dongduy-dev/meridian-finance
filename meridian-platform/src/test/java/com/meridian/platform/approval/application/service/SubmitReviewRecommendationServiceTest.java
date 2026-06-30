package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.dto.ReviewRecommendationDto;
import com.meridian.platform.approval.application.dto.ReviewRecommendationRequest;
import com.meridian.platform.approval.application.event.ReviewRecommendationRecordedEvent;
import com.meridian.platform.approval.application.mapper.ApprovalMapper;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationEventPublisher;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationRepository;
import com.meridian.platform.approval.domain.model.ReviewRecommendation;
import com.meridian.platform.approval.domain.model.ReviewRecommendationAction;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubmitReviewRecommendationServiceTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LOAN_OFFICER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");

    private FakeReviewRecommendationRepository repository;
    private FakeReviewRecommendationEventPublisher eventPublisher;
    private SubmitReviewRecommendationService service;

    @BeforeEach
    void setUp() {
        repository = new FakeReviewRecommendationRepository();
        eventPublisher = new FakeReviewRecommendationEventPublisher();
        service = new SubmitReviewRecommendationService(
                repository,
                eventPublisher,
                new FixedCurrentUserProvider(),
                new ApprovalMapper()
        );
    }

    @Test
    void derivesLoanOfficerActorFromCurrentUserAndPublishesEvent() {
        ReviewRecommendationDto result = service.submitReviewRecommendation(
                LOAN_APPLICATION_ID,
                new ReviewRecommendationRequest(
                        ReviewRecommendationAction.RECOMMEND_APPROVAL,
                        null,
                        "ready for approval"
                )
        );

        assertNotNull(result.recommendationId());
        assertEquals(LOAN_APPLICATION_ID, result.loanApplicationId());
        assertEquals(LOAN_OFFICER_USER_ID, result.loanOfficerUserId());
        assertEquals("RECOMMEND_APPROVAL", result.action());
        assertEquals(LOAN_OFFICER_USER_ID, repository.savedRecommendation.loanOfficerUserId());
        assertEquals(result.recommendationId(), eventPublisher.publishedEvent.recommendationId());
        assertEquals(LOAN_APPLICATION_ID, eventPublisher.publishedEvent.loanApplicationId());
    }

    @Test
    void rejectsCorrectionRecommendationWithoutReason() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.submitReviewRecommendation(
                        LOAN_APPLICATION_ID,
                        new ReviewRecommendationRequest(
                                ReviewRecommendationAction.REQUEST_STAFF_CORRECTION,
                                " ",
                                null
                        )
                )
        );

        assertEquals("RECOMMENDATION_REASON_REQUIRED", exception.getErrorCode());
    }

    @Test
    void propagatesEventPublicationFailureForTransactionRollback() {
        eventPublisher.failure = new IllegalStateException("loan rejected transition");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.submitReviewRecommendation(
                        LOAN_APPLICATION_ID,
                        new ReviewRecommendationRequest(
                                ReviewRecommendationAction.RECOMMEND_APPROVAL,
                                null,
                                null
                        )
                )
        );

        assertEquals("loan rejected transition", exception.getMessage());
    }

    private static class FixedCurrentUserProvider implements CurrentUserProvider {

        @Override
        public AuthenticatedUser currentUser() {
            return new AuthenticatedUser(
                    LOAN_OFFICER_USER_ID,
                    "loan.officer@meridian.local",
                    "STAFF",
                    null,
                    Set.of("LOAN_OFFICER"),
                    Set.of("loan:review", "approval:recommend")
            );
        }
    }

    private static class FakeReviewRecommendationRepository implements ReviewRecommendationRepository {

        private ReviewRecommendation savedRecommendation;

        @Override
        public ReviewRecommendation save(ReviewRecommendation recommendation) {
            savedRecommendation = recommendation;
            return recommendation;
        }
    }

    private static class FakeReviewRecommendationEventPublisher implements ReviewRecommendationEventPublisher {

        private ReviewRecommendationRecordedEvent publishedEvent;
        private RuntimeException failure;

        @Override
        public void publish(ReviewRecommendationRecordedEvent event) {
            publishedEvent = event;
            if (failure != null) {
                throw failure;
            }
        }
    }
}
