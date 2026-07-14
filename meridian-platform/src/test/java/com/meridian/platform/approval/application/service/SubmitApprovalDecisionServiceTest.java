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
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubmitApprovalDecisionServiceTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RECOMMENDATION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID LOAN_OFFICER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID APPROVER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 11, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private FakeReviewRecommendationRepository reviewRecommendationRepository;
    private FakeApprovalDecisionRepository approvalDecisionRepository;
    private FakeApprovalDecisionEventPublisher eventPublisher;
    private FakeBusinessAuditPublisher auditPublisher;
    private SubmitApprovalDecisionService service;

    @BeforeEach
    void setUp() {
        reviewRecommendationRepository = new FakeReviewRecommendationRepository();
        approvalDecisionRepository = new FakeApprovalDecisionRepository();
        eventPublisher = new FakeApprovalDecisionEventPublisher();
        auditPublisher = new FakeBusinessAuditPublisher();
        service = newService(APPROVER_USER_ID);
    }

    @Test
    void derivesApproverActorFromCurrentUserAuditsAndPublishesEvent() {
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
        assertEquals(NOW, result.decidedAt());
        assertEquals(APPROVER_USER_ID, approvalDecisionRepository.savedDecision.approverUserId());
        assertEquals(result.decisionId(), eventPublisher.publishedEvent.decisionId());
        assertEquals(RECOMMENDATION_ID, eventPublisher.publishedEvent.reviewRecommendationId());
        assertEquals(auditPublisher.publishedEvent.operationContext(), eventPublisher.publishedEvent.operationContext());
        assertEquals(BusinessAuditAction.APPROVAL_DECISION_RECORDED,
                auditPublisher.publishedEvent.entries().getFirst().action());
    }

    @Test
    void rejectsUnavailableRevisionActionBeforeAnyEffect() {
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.submitApprovalDecision(
                        LOAN_APPLICATION_ID,
                        new ApprovalDecisionRequest(
                                ApprovalDecisionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION,
                                "Correction required.",
                                null
                        )
                )
        );

        assertEquals("REVISION_WORKFLOW_NOT_AVAILABLE", exception.getErrorCode());
        assertNull(approvalDecisionRepository.savedDecision);
        assertNull(auditPublisher.publishedEvent);
        assertNull(eventPublisher.publishedEvent);
    }

    @ParameterizedTest
    @EnumSource(
            value = ApprovalDecisionAction.class,
            names = {"REJECT", "RETURN_TO_LOAN_OFFICER_REVIEW"}
    )
    void recordsOtherNonApprovalActions(ApprovalDecisionAction action) {
        ApprovalDecisionDto result = service.submitApprovalDecision(
                LOAN_APPLICATION_ID,
                new ApprovalDecisionRequest(action, "Decision reason.", null)
        );

        assertEquals(action.name(), result.action());
        assertNotNull(approvalDecisionRepository.savedDecision);
        assertNotNull(auditPublisher.publishedEvent);
        assertNotNull(eventPublisher.publishedEvent);
    }

    @Test
    void rejectsSameUserMakerCheckerViolation() {
        service = newService(LOAN_OFFICER_USER_ID);

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

    private SubmitApprovalDecisionService newService(UUID userId) {
        return new SubmitApprovalDecisionService(
                reviewRecommendationRepository,
                approvalDecisionRepository,
                eventPublisher,
                new FixedCurrentUserProvider(userId),
                new ApprovalMapper(),
                auditPublisher,
                CLOCK
        );
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

    private static class FakeBusinessAuditPublisher implements BusinessAuditPublisher {

        private BusinessAuditEvent publishedEvent;

        @Override
        public void publish(BusinessAuditEvent event) {
            publishedEvent = event;
        }
    }
}
