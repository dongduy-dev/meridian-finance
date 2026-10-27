package com.meridian.platform.loan.infrastructure.adapter.in.approval;

import com.meridian.platform.loan.application.port.in.QueryApprovalLoanCaseUseCase;
import com.meridian.platform.loan.application.port.in.QueryApprovalLoanCaseUseCase.*;
import com.meridian.platform.loan.application.port.in.QueryApprovalLoanReviewCycleUseCase;
import com.meridian.platform.loan.application.port.in.QueryApprovalLoanReviewHistoryUseCase;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovalLoanSpiAdaptersTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 2, 9, 1, 2, 123456000);

    @Test
    void caseAndQueueTranslateEveryFieldAndNestedFactWithoutChangingOrderOrNulls() {
        var input = mock(QueryApprovalLoanCaseUseCase.class);
        var adapter = new ApprovalLoanCaseAdapter(input);
        UUID id = UUID.randomUUID();
        var cycle = new ReviewCycleSnapshot(UUID.randomUUID(), 4, UUID.randomUUID(), "COMPLETED", TIME, TIME.plusHours(1));
        var scopes = new ArrayList<>(List.of("DOCUMENT_REPLACEMENT", "DOCUMENT_REVIEW"));
        var options = new ArrayList<>(List.of(new CorrectionOptionSnapshot("BANK_STATEMENT", UUID.randomUUID(),
                UUID.randomUUID(), scopes), new CorrectionOptionSnapshot("RECENT_PAYSLIP", null, null,
                List.of("SUPPORTING_DOCUMENT_UPLOAD"))));
        for (var current : new ReviewCycleSnapshot[]{cycle, null}) {
            var source = new CaseSnapshot(id, UUID.randomUUID(), "APP-EXACT", "UNSECURED_CONSUMER_LOAN", "UNSECURED",
                    new BigDecimal("1234567.89"), 9, "APPROVAL_PENDING", TIME, "STAFF_ASSISTED",
                    new DocumentReadinessSnapshot(true, false), new ProductReadinessSnapshot("VERIFIED", false),
                    current, options);
            when(input.findCase(id)).thenReturn(Optional.of(source));
            var actual = adapter.findCase(id).orElseThrow();
            assertEquals(facts(source), facts(actual));
            assertNotSame(source.correctionOptions(), actual.correctionOptions());
            assertThrows(UnsupportedOperationException.class, () -> actual.correctionOptions().clear());
            assertThrows(UnsupportedOperationException.class, () -> actual.correctionOptions().getFirst().allowedScopes().clear());
        }
        scopes.clear();
        options.clear();
        assertEquals(2, adapter.findCase(id).orElseThrow().correctionOptions().size());
        var source = new QueuePageSnapshot(2, 3, 8, 3, List.of(
                new QueueItemSnapshot(id, "APP-FIRST", "COLLATERAL_LOAN", "SECURED", BigDecimal.TEN, 7,
                        "APPROVAL_PENDING", TIME),
                new QueueItemSnapshot(UUID.randomUUID(), "APP-LAST", "SALARY_ADVANCE", "SALARY_BASED", BigDecimal.ONE, 1,
                        "APPROVAL_PENDING", null)));
        when(input.findDecisionQueue(null, 2, 3)).thenReturn(source);
        var queue = adapter.findDecisionQueue(null, 2, 3);
        assertEquals(facts(source), facts(queue));
        assertThrows(UnsupportedOperationException.class, () -> queue.items().clear());
        verify(input).findDecisionQueue(null, 2, 3);
    }

    @Test
    void cycleAndHistoryPreserveExactIdsLegacyAssignmentsEveryTimestampAndCompleteOrder() {
        UUID id = UUID.randomUUID();
        var cycles = mock(QueryApprovalLoanReviewCycleUseCase.class);
        var cycleAdapter = new ApprovalLoanReviewCycleAdapter(cycles);
        for (UUID officer : new UUID[]{UUID.randomUUID(), null}) {
            var source = new QueryApprovalLoanReviewCycleUseCase.ActiveReviewCycleSnapshot(UUID.randomUUID(), officer);
            when(cycles.findActiveReviewCycle(id)).thenReturn(Optional.of(source));
            assertEquals(facts(source), facts(cycleAdapter.findActiveReviewCycle(id).orElseThrow()));
        }
        verify(cycles, times(2)).findActiveReviewCycle(id);
        verifyNoMoreInteractions(cycles);
        var histories = mock(QueryApprovalLoanReviewHistoryUseCase.class);
        var sourceCycles = new ArrayList<>(List.of(
                new ReviewCycleSnapshot(UUID.randomUUID(), 1, null, "COMPLETED", TIME, TIME.plusHours(1)),
                new ReviewCycleSnapshot(UUID.randomUUID(), 2, UUID.randomUUID(), "ACTIVE", TIME.plusHours(2), null)));
        var source = new QueryApprovalLoanReviewHistoryUseCase.HistorySnapshot(id, "APP-HISTORY", "UNDER_REVIEW", sourceCycles);
        when(histories.findHistory(id)).thenReturn(Optional.of(source));
        var actual = new ApprovalLoanReviewHistoryAdapter(histories).findHistory(id).orElseThrow();
        assertEquals(facts(source), facts(actual));
        sourceCycles.clear();
        assertEquals(2, actual.cycles().size());
        assertThrows(UnsupportedOperationException.class, () -> actual.cycles().clear());
        verify(histories).findHistory(id);
        verifyNoMoreInteractions(histories);
    }

    @Test
    void everyOptionalAndFailureIsDelegatedExactlyWithoutFallbacks() {
        UUID id = UUID.randomUUID();
        var cases = mock(QueryApprovalLoanCaseUseCase.class);
        var cycles = mock(QueryApprovalLoanReviewCycleUseCase.class);
        var history = mock(QueryApprovalLoanReviewHistoryUseCase.class);
        var caseAdapter = new ApprovalLoanCaseAdapter(cases);
        var cycleAdapter = new ApprovalLoanReviewCycleAdapter(cycles);
        var historyAdapter = new ApprovalLoanReviewHistoryAdapter(history);
        assertTrue(caseAdapter.findCase(id).isEmpty());
        assertTrue(cycleAdapter.findActiveReviewCycle(id).isEmpty());
        assertTrue(historyAdapter.findHistory(id).isEmpty());
        var failure = new IllegalStateException("same provider failure");
        when(cases.findCase(id)).thenThrow(failure);
        when(cases.findDecisionQueue("COLLATERAL_LOAN", 3, 10)).thenThrow(failure);
        when(cycles.findActiveReviewCycle(id)).thenThrow(failure);
        when(history.findHistory(id)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> caseAdapter.findCase(id)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> caseAdapter.findDecisionQueue("COLLATERAL_LOAN", 3, 10)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> cycleAdapter.findActiveReviewCycle(id)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> historyAdapter.findHistory(id)));
        verify(cases, times(2)).findCase(id);
        verify(cases).findDecisionQueue("COLLATERAL_LOAN", 3, 10);
        verify(cycles, times(2)).findActiveReviewCycle(id);
        verify(history, times(2)).findHistory(id);
        verifyNoMoreInteractions(cases, cycles, history);
    }

    // Compare the complete named record shape across independently owned Java contracts.
    private static Object facts(Object value) {
        if (value == null) return null;
        if (value instanceof List<?> list) return list.stream().map(ApprovalLoanSpiAdaptersTest::facts).toList();
        if (!value.getClass().isRecord()) return value;
        Map<String, Object> result = new LinkedHashMap<>();
        for (var component : value.getClass().getRecordComponents()) {
            try {
                result.put(component.getName(), facts(component.getAccessor().invoke(value)));
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
        return result;
    }
}
