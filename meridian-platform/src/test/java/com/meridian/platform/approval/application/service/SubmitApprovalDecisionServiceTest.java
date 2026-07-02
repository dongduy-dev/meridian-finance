package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.dto.ApprovalDecisionDto;
import com.meridian.platform.approval.application.dto.ApprovalDecisionRequest;
import com.meridian.platform.approval.application.event.ApprovalDecisionRecordedEvent;
import com.meridian.platform.approval.application.mapper.ApprovalMapper;
import com.meridian.platform.approval.application.port.out.ApprovalDecisionEventPublisher;
import com.meridian.platform.approval.application.port.out.ApprovalDecisionRepository;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationRepository;
import com.meridian.platform.approval.domain.model.ApprovalDecision;
import com.meridian.platform.approval.domain.model.ApprovalDecisionAction;
import com.meridian.platform.approval.domain.model.ReviewRecommendation;
import com.meridian.platform.approval.domain.model.ReviewRecommendationAction;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubmitApprovalDecisionServiceTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RECOMMENDATION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID LOAN_OFFICER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID APPROVER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");

    private FakeReviewRecommendationRepository reviewRecommendationRepository;
    private FakeApprovalDecisionRepository approvalDecisionRepository;
    private FakeApprovalDecisionEventPublisher eventPublisher;
    private SubmitApprovalDecisionService service;

    @BeforeEach
    void setUp() {
        reviewRecommendationRepository = new FakeReviewRecommendationRepository();
        approvalDecisionRepository = new FakeApprovalDecisionRepository();
        eventPublisher = new FakeApprovalDecisionEventPublisher();
        service = new SubmitApprovalDecisionService(
                reviewRecommendationRepository,
                approvalDecisionRepository,
                eventPublisher,
                new FixedCurrentUserProvider(APPROVER_USER_ID),
                new ApprovalMapper()
        );
    }

    @Test
    void derivesApproverActorFromCurrentUserAndPublishesEvent() {
        ApprovalDecisionDto result = service.submitApprovalDecision(
                LOAN_APPLICATION_ID,
                new ApprovalDecisionRequest(
                        ApprovalDecisionAction.APPROVE,
                        null,
                        "approved"
                )
        );

        assertNotNull(result.decisionId());
        assertEquals(LOAN_APPLICATION_ID, result.loanApplicationId());
        assertEquals(RECOMMENDATION_ID, result.reviewRecommendationId());
        assertEquals(APPROVER_USER_ID, result.approverUserId());
        assertEquals("APPROVE", result.action());
        assertEquals(APPROVER_USER_ID, approvalDecisionRepository.savedDecision.approverUserId());
        assertEquals(result.decisionId(), eventPublisher.publishedEvent.decisionId());
        assertEquals(RECOMMENDATION_ID, eventPublisher.publishedEvent.reviewRecommendationId());
    }

    @Test
    void rejectsSameUserMakerCheckerViolation() {
        service = new SubmitApprovalDecisionService(
                reviewRecommendationRepository,
                approvalDecisionRepository,
                eventPublisher,
                new FixedCurrentUserProvider(LOAN_OFFICER_USER_ID),
                new ApprovalMapper()
        );

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.submitApprovalDecision(
                        LOAN_APPLICATION_ID,
                        new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
                )
        );

        assertEquals("MAKER_CHECKER_VIOLATION", exception.getErrorCode());
    }

    @Test
    void requiresPriorReviewRecommendation() {
        reviewRecommendationRepository.latestRecommendation = Optional.empty();

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.submitApprovalDecision(
                        LOAN_APPLICATION_ID,
                        new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
                )
        );

        assertEquals("REVIEW_RECOMMENDATION_REQUIRED", exception.getErrorCode());
    }

    @Test
    void propagatesEventPublicationFailureForTransactionRollback() {
        eventPublisher.failure = new IllegalStateException("loan rejected transition");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.submitApprovalDecision(
                        LOAN_APPLICATION_ID,
                        new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
                )
        );

        assertEquals("loan rejected transition", exception.getMessage());
    }

    private static ReviewRecommendation recommendation() {
        return ReviewRecommendation.recorded(
                RECOMMENDATION_ID,
                LOAN_APPLICATION_ID,
                LOAN_OFFICER_USER_ID,
                ReviewRecommendationAction.RECOMMEND_APPROVAL,
                null,
                null,
                LocalDateTime.now()
        );
    }

    private static class FixedCurrentUserProvider implements CurrentUserProvider {

        private final UUID userId;

        private FixedCurrentUserProvider(UUID userId) {
            this.userId = userId;
        }

        @Override
        public AuthenticatedUser currentUser() {
            return new AuthenticatedUser(
                    userId,
                    "approver@meridian.local",
                    "STAFF",
                    null,
                    Set.of("APPROVER"),
                    Set.of("loan:read", "approval:decide")
            );
        }
    }

    private static class FakeReviewRecommendationRepository implements ReviewRecommendationRepository {

        private Optional<ReviewRecommendation> latestRecommendation = Optional.of(recommendation());

        @Override
        public ReviewRecommendation save(ReviewRecommendation recommendation) {
            latestRecommendation = Optional.of(recommendation);
            return recommendation;
        }

        @Override
        public Optional<ReviewRecommendation> findLatestByLoanApplicationId(UUID loanApplicationId) {
            return latestRecommendation
                    .filter(recommendation -> recommendation.loanApplicationId().equals(loanApplicationId));
        }
    }

    private static class FakeApprovalDecisionRepository implements ApprovalDecisionRepository {

        private ApprovalDecision savedDecision;

        @Override
        public ApprovalDecision save(ApprovalDecision approvalDecision) {
            savedDecision = approvalDecision;
            return approvalDecision;
        }
    }

    private static class FakeApprovalDecisionEventPublisher implements ApprovalDecisionEventPublisher {

        private ApprovalDecisionRecordedEvent publishedEvent;
        private RuntimeException failure;

        @Override
        public void publish(ApprovalDecisionRecordedEvent event) {
            publishedEvent = event;
            if (failure != null) {
                throw failure;
            }
        }
    }
}
