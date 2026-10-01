package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.port.out.*;
import com.meridian.platform.approval.domain.model.*;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryStaffReviewHistoryServiceTest {
    private static final UUID APP = UUID.randomUUID();
    private static final UUID OFFICER = UUID.randomUUID();
    private static final UUID APPROVER = UUID.randomUUID();
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 1, 9, 0);
    @Mock ApprovalLoanReviewHistoryPort loanHistory;
    @Mock ReviewRecommendationRepository recommendations;
    @Mock ApprovalDecisionRepository decisions;
    @Mock StaffActorDirectoryPort actors;
    @Mock CurrentUserProvider currentUser;
    private QueryStaffReviewHistoryService service;

    @BeforeEach
    void setUp() {
        service = new QueryStaffReviewHistoryService(loanHistory, recommendations, decisions, actors, currentUser);
    }

    @ParameterizedTest
    @ValueSource(strings = {"loan:review", "approval:recommend", "approval:decide"})
    void returnsExactLinkedCyclesIncludingIncompleteCyclesAndNormalReasons(String permission) {
        var first = cycle(1, OFFICER);
        var second = cycle(2, OFFICER);
        var third = cycle(3, OFFICER);
        var recommendation = recommendation(first.reviewCycleId(), OFFICER);
        var pending = recommendation(second.reviewCycleId(), OFFICER);
        var decision = decision(recommendation.id());
        arrange(OFFICER, permission, List.of(third, first, second), List.of(pending, recommendation), List.of(decision));
        when(actors.findByUserIds(anySet())).thenReturn(Map.of(
                OFFICER, new StaffActorSummary(OFFICER, "Loan Officer", "officer@meridian.test"),
                APPROVER, new StaffActorSummary(APPROVER, "Approver", "approver@meridian.test")));

        var result = service.query(APP);

        assertEquals(List.of(1, 2, 3), result.cycles().stream().map(value -> value.cycleNumber()).toList());
        var complete = result.cycles().getFirst();
        assertEquals(first.reviewCycleId(), complete.reviewCycleId());
        assertEquals(OFFICER, complete.assignedLoanOfficer().userId());
        assertEquals(first.reviewCycleId(), complete.recommendation().reviewCycleId());
        assertEquals(recommendation.id(), complete.decision().reviewRecommendationId());
        assertEquals("Evidence supports review", complete.recommendation().reason());
        assertEquals("Further credit review required", complete.decision().reason());
        assertEquals("Recommendation assessment fixture", complete.recommendation().internalNotes());
        assertEquals("Decision assessment fixture", complete.decision().internalNotes());
        assertEquals(AT, complete.recommendation().submittedAt());
        assertEquals(AT, complete.decision().decidedAt());
        assertNull(result.cycles().get(1).decision());
        assertNull(result.cycles().get(2).recommendation());
        verify(actors).findByUserIds(Set.of(OFFICER, APPROVER));
    }

    @Test
    void unrelatedReviewWorkerSeesNormalReasonsButNeitherCreditNote() {
        var cycle = cycle(1, OFFICER);
        var recommendation = recommendation(cycle.reviewCycleId(), OFFICER);
        arrange(UUID.randomUUID(), "loan:review", List.of(cycle), List.of(recommendation), List.of(decision(recommendation.id())));
        var result = service.query(APP).cycles().getFirst();
        assertEquals("Further credit review required", result.decision().reason());
        assertFalse(result.recommendation().internalNoteReadable());
        assertFalse(result.decision().internalNoteReadable());
        assertNull(result.recommendation().internalNotes());
        assertNull(result.decision().internalNotes());
    }

    @Test
    void unresolvedLegacyAssignmentDoesNotGrantDecisionNoteToRecommendationAuthor() {
        var cycle = cycle(1, null);
        var recommendation = recommendation(cycle.reviewCycleId(), OFFICER);
        arrange(OFFICER, "approval:recommend", List.of(cycle), List.of(recommendation), List.of(decision(recommendation.id())));
        var result = service.query(APP).cycles().getFirst();
        assertNull(result.assignedLoanOfficer());
        assertNull(result.recommendation().recordedBy());
        assertNull(result.decision().recordedBy());
        assertTrue(result.recommendation().internalNoteReadable());
        assertNotNull(result.recommendation().internalNotes());
        assertFalse(result.decision().internalNoteReadable());
        assertNull(result.decision().internalNotes());
    }

    @Test
    void approverReadsLegacyNotesWithoutInventingMissingStaffDisplay() {
        var cycle = cycle(1, null);
        var recommendation = recommendation(cycle.reviewCycleId(), OFFICER);
        arrange(APPROVER, "approval:decide", List.of(cycle), List.of(recommendation), List.of(decision(recommendation.id())));
        var result = service.query(APP).cycles().getFirst();
        assertNull(result.recommendation().recordedBy());
        assertNotNull(result.recommendation().internalNotes());
        assertNotNull(result.decision().internalNotes());
    }

    @ParameterizedTest
    @ValueSource(strings = {"loan:read", "audit:read", "loan:disburse", "admin:config", "approval:decide:all"})
    void genericOrUnrelatedPermissionsCannotReadHistoryEvenForAuthor(String permission) {
        when(currentUser.currentUser()).thenReturn(staff(OFFICER, permission));
        assertThrows(AuthorizationException.class, () -> service.query(APP));
        verifyNoInteractions(loanHistory, recommendations, decisions, actors);
    }

    @Test
    void customerPrincipalCannotUseLendingHistoryPermission() {
        when(currentUser.currentUser()).thenReturn(new AuthenticatedUser(OFFICER, "customer@meridian.test",
                "CUSTOMER", UUID.randomUUID(), Set.of(), Set.of("approval:decide")));
        assertThrows(AuthorizationException.class, () -> service.query(APP));
        verifyNoInteractions(loanHistory);
    }

    @Test
    void absentApplicationIsNotFound() {
        when(currentUser.currentUser()).thenReturn(staff(OFFICER, "loan:review"));
        when(loanHistory.findHistory(APP)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.query(APP));
    }

    @Test
    void applicationWithoutReviewHasEmptyHistory() {
        arrange(OFFICER, "loan:review", List.of(), List.of(), List.of());
        assertTrue(service.query(APP).cycles().isEmpty());
    }

    @Test
    void orphanRecommendationFailsClosedRatherThanJoiningByTime() {
        arrange(OFFICER, "loan:review", List.of(cycle(1, OFFICER)),
                List.of(recommendation(UUID.randomUUID(), OFFICER)), List.of());
        assertThrows(BusinessStateConflictException.class, () -> service.query(APP));
    }

    @Test
    void orphanDecisionFailsClosedRatherThanJoiningByTime() {
        var cycle = cycle(1, OFFICER);
        arrange(OFFICER, "loan:review", List.of(cycle), List.of(recommendation(cycle.reviewCycleId(), OFFICER)),
                List.of(decision(UUID.randomUUID())));
        assertThrows(BusinessStateConflictException.class, () -> service.query(APP));
    }

    private void arrange(UUID actor, String permission, List<ApprovalLoanCasePort.ReviewCycleSnapshot> cycles,
                         List<ReviewRecommendation> recs, List<ApprovalDecision> decs) {
        when(currentUser.currentUser()).thenReturn(staff(actor, permission));
        when(loanHistory.findHistory(APP)).thenReturn(Optional.of(
                new ApprovalLoanReviewHistoryPort.HistorySnapshot(APP, "UCL-HISTORY", "RETURNED_TO_REVIEW", cycles)));
        lenient().when(recommendations.findByLoanApplicationIdOrderBySubmittedAtAsc(APP)).thenReturn(recs);
        lenient().when(decisions.findByLoanApplicationIdOrderByDecidedAtDesc(APP)).thenReturn(decs);
        lenient().when(actors.findByUserIds(anySet())).thenReturn(Map.of());
    }

    private static AuthenticatedUser staff(UUID id, String permission) {
        return new AuthenticatedUser(id, "staff@meridian.test", "STAFF", null, Set.of(), Set.of(permission));
    }

    private static ApprovalLoanCasePort.ReviewCycleSnapshot cycle(int number, UUID assigned) {
        return new ApprovalLoanCasePort.ReviewCycleSnapshot(UUID.randomUUID(), number, assigned,
                number == 3 ? "ACTIVE" : "SUPERSEDED", AT, number == 3 ? null : AT.plusHours(1));
    }

    private static ReviewRecommendation recommendation(UUID cycle, UUID author) {
        return ReviewRecommendation.recorded(UUID.randomUUID(), APP, cycle, author,
                ReviewRecommendationAction.RECOMMEND_APPROVAL, "Evidence supports review", null,
                "Recommendation assessment fixture", AT);
    }

    private static ApprovalDecision decision(UUID recommendation) {
        return ApprovalDecision.recorded(UUID.randomUUID(), APP, recommendation, APPROVER,
                ApprovalDecisionAction.RETURN_TO_LOAN_OFFICER_REVIEW, "Further credit review required", null,
                "Decision assessment fixture", AT);
    }
}
