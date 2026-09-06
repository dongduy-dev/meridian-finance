package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.port.out.ApprovalDecisionRepository;
import com.meridian.platform.approval.application.port.out.ApprovalLoanCasePort;
import com.meridian.platform.approval.application.port.out.ReviewRecommendationRepository;
import com.meridian.platform.approval.application.dto.StaffApprovalQueuePageDto;
import com.meridian.platform.approval.application.dto.StaffDecisionCaseDto;
import com.meridian.platform.approval.application.dto.StaffRecommendationCaseDto;
import com.meridian.platform.approval.domain.model.ApprovalDecision;
import com.meridian.platform.approval.domain.model.ApprovalDecisionAction;
import com.meridian.platform.approval.domain.model.ReviewRecommendation;
import com.meridian.platform.approval.domain.model.ReviewRecommendationAction;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryStaffApprovalWorkServiceTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID CYCLE_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID RECOMMENDATION_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID OFFICER_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    private static final UUID APPROVER_ID = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 6, 9, 0);

    @Mock ApprovalLoanCasePort loanCases;
    @Mock ReviewRecommendationRepository recommendations;
    @Mock ApprovalDecisionRepository decisions;
    @Mock CurrentUserProvider currentUserProvider;

    private QueryStaffApprovalWorkService service;

    @BeforeEach
    void setUp() {
        service = new QueryStaffApprovalWorkService(
                loanCases, recommendations, decisions, currentUserProvider
        );
    }

    @Test
    void recommendationReadUsesCurrentCycleAndBackendAvailability() {
        when(currentUserProvider.currentUser()).thenReturn(staff(APPROVER_ID, Set.of("approval:recommend")));
        when(loanCases.findCase(APPLICATION_ID)).thenReturn(Optional.of(caseSnapshot("UNDER_REVIEW")));
        when(recommendations.findByReviewCycleId(CYCLE_ID)).thenReturn(Optional.empty());

        var result = service.queryRecommendationCase(APPLICATION_ID);

        assertTrue(result.recommendationAvailable());
        assertNull(result.recommendation());
        assertEquals(CYCLE_ID, result.evidence().currentReviewCycle().reviewCycleId());
        assertEquals(List.of("DOCUMENT_REPLACEMENT", "DOCUMENT_REVIEW"),
                result.correctionOptions().getFirst().allowedScopes());
    }

    @Test
    void recommendationReadReturnsExactDurableCurrentCycleProvenance() {
        when(currentUserProvider.currentUser()).thenReturn(staff(APPROVER_ID, Set.of("approval:recommend")));
        when(loanCases.findCase(APPLICATION_ID)).thenReturn(Optional.of(caseSnapshot("UNDER_REVIEW")));
        when(recommendations.findByReviewCycleId(CYCLE_ID)).thenReturn(Optional.of(recommendation()));

        var result = service.queryRecommendationCase(APPLICATION_ID);

        assertFalse(result.recommendationAvailable());
        assertEquals(RECOMMENDATION_ID, result.recommendation().recommendationId());
        assertEquals(CYCLE_ID, result.recommendation().reviewCycleId());
        assertEquals("RECOMMEND_APPROVAL", result.recommendation().action());
    }

    @Test
    void decisionReadExposesRelationInsteadOfActorIdentifiersAndReturnsHistory() {
        ReviewRecommendation recommendation = recommendation();
        ApprovalDecision decision = ApprovalDecision.recorded(
                UUID.randomUUID(), APPLICATION_ID, RECOMMENDATION_ID, APPROVER_ID,
                ApprovalDecisionAction.APPROVE, null, null, "restricted", NOW
        );
        when(currentUserProvider.currentUser()).thenReturn(staff(APPROVER_ID, Set.of("approval:decide")));
        when(loanCases.findCase(APPLICATION_ID)).thenReturn(Optional.of(caseSnapshot("CUSTOMER_ACCEPTANCE_PENDING")));
        when(recommendations.findLatestByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.of(recommendation));
        when(decisions.findByLoanApplicationIdOrderByDecidedAtDesc(APPLICATION_ID)).thenReturn(List.of(decision));
        when(decisions.findByReviewRecommendationId(RECOMMENDATION_ID)).thenReturn(Optional.of(decision));

        var result = service.queryDecisionCase(APPLICATION_ID);

        assertTrue(result.makerCheckerEligible());
        assertFalse(result.decisionAvailable());
        assertEquals("APPROVE", result.latestDecision().action());
        assertEquals(1, result.decisionHistory().size());
    }

    @Test
    void makerCheckerBlocksCurrentRecommendationAuthor() {
        when(currentUserProvider.currentUser()).thenReturn(staff(OFFICER_ID, Set.of("approval:decide")));
        when(loanCases.findCase(APPLICATION_ID)).thenReturn(Optional.of(caseSnapshot("APPROVAL_PENDING")));
        when(recommendations.findLatestByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.of(recommendation()));
        when(decisions.findByLoanApplicationIdOrderByDecidedAtDesc(APPLICATION_ID)).thenReturn(List.of());
        when(decisions.findByReviewRecommendationId(RECOMMENDATION_ID)).thenReturn(Optional.empty());

        var result = service.queryDecisionCase(APPLICATION_ID);

        assertFalse(result.makerCheckerEligible());
        assertFalse(result.decisionAvailable());
    }

    @Test
    void missingRecommendationAndDecisionRemainExplicitlyUnavailable() {
        when(currentUserProvider.currentUser()).thenReturn(staff(APPROVER_ID, Set.of("approval:decide")));
        when(loanCases.findCase(APPLICATION_ID)).thenReturn(Optional.of(caseSnapshot("APPROVAL_PENDING")));
        when(recommendations.findLatestByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());
        when(decisions.findByLoanApplicationIdOrderByDecidedAtDesc(APPLICATION_ID)).thenReturn(List.of());

        var result = service.queryDecisionCase(APPLICATION_ID);

        assertNull(result.recommendation());
        assertNull(result.latestDecision());
        assertFalse(result.makerCheckerEligible());
        assertFalse(result.decisionAvailable());
    }

    @Test
    void missingLoanApplicationUsesTheEstablishedConcealedNotFoundContract() {
        when(currentUserProvider.currentUser()).thenReturn(staff(APPROVER_ID, Set.of("approval:decide")));
        when(loanCases.findCase(APPLICATION_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.queryDecisionCase(APPLICATION_ID));
    }

    @Test
    void queueMembershipAndPagingRemainLoanServerOwned() {
        ApprovalLoanCasePort.QueueItemSnapshot item = new ApprovalLoanCasePort.QueueItemSnapshot(
                APPLICATION_ID, "UCL-1", "UNSECURED_CONSUMER_LOAN", "PERSONAL",
                BigDecimal.TEN, 6, "APPROVAL_PENDING", NOW.minusHours(1)
        );
        when(currentUserProvider.currentUser()).thenReturn(staff(APPROVER_ID, Set.of("approval:decide")));
        when(loanCases.findDecisionQueue("UNSECURED_CONSUMER_LOAN", 1, 25)).thenReturn(
                new ApprovalLoanCasePort.QueuePageSnapshot(1, 25, 26, 2, List.of(item))
        );
        when(recommendations.findLatestByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.of(recommendation()));
        when(decisions.findByReviewRecommendationId(RECOMMENDATION_ID)).thenReturn(Optional.empty());

        var result = service.queryDecisionQueue("unsecured_consumer_loan", 1, 25);

        assertEquals(26, result.totalElements());
        assertTrue(result.items().getFirst().decisionAvailable());
        verify(loanCases).findDecisionQueue("UNSECURED_CONSUMER_LOAN", 1, 25);
    }

    @Test
    void queueFailsClosedWhenApprovalEvidenceContradictsPendingMembership() {
        ApprovalLoanCasePort.QueueItemSnapshot item = new ApprovalLoanCasePort.QueueItemSnapshot(
                APPLICATION_ID, "UCL-1", "UNSECURED_CONSUMER_LOAN", "PERSONAL",
                BigDecimal.TEN, 6, "APPROVAL_PENDING", NOW.minusHours(1)
        );
        ApprovalDecision decision = ApprovalDecision.recorded(
                UUID.randomUUID(), APPLICATION_ID, RECOMMENDATION_ID, UUID.randomUUID(),
                ApprovalDecisionAction.APPROVE, null, null, null, NOW
        );
        when(currentUserProvider.currentUser()).thenReturn(staff(APPROVER_ID, Set.of("approval:decide")));
        when(loanCases.findDecisionQueue(null, 0, 25)).thenReturn(
                new ApprovalLoanCasePort.QueuePageSnapshot(0, 25, 1, 1, List.of(item))
        );
        when(recommendations.findLatestByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.of(recommendation()));
        when(decisions.findByReviewRecommendationId(RECOMMENDATION_ID)).thenReturn(Optional.of(decision));

        assertThrows(BusinessStateConflictException.class,
                () -> service.queryDecisionQueue(null, 0, 25));
    }

    @Test
    void exactPermissionAndStaffShapeAreRequired() {
        for (AuthenticatedUser actor : List.of(
                staff(APPROVER_ID, Set.of("approval:decide:all")),
                staff(APPROVER_ID, Set.of("loan:read")),
                new AuthenticatedUser(APPROVER_ID, "customer@test", "CUSTOMER", UUID.randomUUID(),
                        Set.of("APPROVER"), Set.of("approval:decide"))
        )) {
            when(currentUserProvider.currentUser()).thenReturn(actor);
            assertThrows(AuthorizationException.class,
                    () -> service.queryDecisionCase(APPLICATION_ID));
        }
    }

    @Test
    void purposeLimitedDtosHaveNoActorOrInternalNoteFields() {
        for (Class<?> type : List.of(
                StaffRecommendationCaseDto.class,
                StaffRecommendationCaseDto.RecommendationDto.class,
                StaffDecisionCaseDto.class,
                StaffDecisionCaseDto.DecisionDto.class,
                StaffApprovalQueuePageDto.ItemDto.class
        )) {
            Set<String> names = Arrays.stream(type.getRecordComponents())
                    .map(component -> component.getName())
                    .collect(java.util.stream.Collectors.toSet());
            assertFalse(names.contains("loanOfficerUserId"));
            assertFalse(names.contains("approverUserId"));
            assertFalse(names.contains("internalNotes"));
            assertFalse(names.contains("customerId"));
        }
    }

    private static ApprovalLoanCasePort.CaseSnapshot caseSnapshot(String status) {
        return new ApprovalLoanCasePort.CaseSnapshot(
                APPLICATION_ID, "UCL-1", "UNSECURED_CONSUMER_LOAN", "PERSONAL",
                BigDecimal.TEN, 6, status, NOW.minusHours(2),
                new ApprovalLoanCasePort.DocumentReadinessSnapshot(true, true),
                new ApprovalLoanCasePort.ProductReadinessSnapshot("VERIFIED", true),
                new ApprovalLoanCasePort.ReviewCycleSnapshot(CYCLE_ID, 1, "ACTIVE", NOW.minusHours(1), null),
                List.of(new ApprovalLoanCasePort.CorrectionOptionSnapshot(
                        "INCOME_PROOF", UUID.randomUUID(), UUID.randomUUID(),
                        List.of("DOCUMENT_REPLACEMENT", "DOCUMENT_REVIEW")
                ))
        );
    }

    private static ReviewRecommendation recommendation() {
        return ReviewRecommendation.recorded(
                RECOMMENDATION_ID, APPLICATION_ID, CYCLE_ID, OFFICER_ID,
                ReviewRecommendationAction.RECOMMEND_APPROVAL, null, null, "restricted", NOW.minusMinutes(30)
        );
    }

    private static AuthenticatedUser staff(UUID userId, Set<String> permissions) {
        return new AuthenticatedUser(userId, "staff@meridian.test", "STAFF", null,
                Set.of("APPROVER"), permissions);
    }
}
