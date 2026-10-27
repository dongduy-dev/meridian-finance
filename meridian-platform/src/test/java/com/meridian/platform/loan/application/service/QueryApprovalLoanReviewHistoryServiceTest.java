package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.domain.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryApprovalLoanReviewHistoryServiceTest {
    @Test
    void returnsAllOrderedCycleFactsWithoutLockingAndSnapshotsTheList() {
        var applications = mock(LoanApplicationRepository.class);
        var cycles = mock(LoanReviewCycleRepository.class);
        var service = new QueryApprovalLoanReviewHistoryService(applications, cycles);
        UUID id = UUID.randomUUID();
        var at = LocalDateTime.of(2026, 10, 1, 8, 0);
        var application = new LoanApplication(id, UUID.randomUUID(), UUID.randomUUID(), "UCL-HISTORY",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED,
                LoanApplicationStatus.UNDER_REVIEW, BigDecimal.TEN, 6, at);
        var first = new LoanApplicationReviewCycle(UUID.randomUUID(), id, 1, null,
                LoanReviewCycleStatus.COMPLETED, at, at.plusHours(1));
        var last = LoanApplicationReviewCycle.active(UUID.randomUUID(), id, 2, UUID.randomUUID(), at.plusHours(2));
        var source = new ArrayList<>(List.of(first, last));
        when(applications.findById(id)).thenReturn(Optional.of(application));
        when(cycles.findByLoanApplicationIdOrderByCycleNumberAsc(id)).thenReturn(source);
        var result = service.findHistory(id).orElseThrow();
        assertEquals(id, result.loanApplicationId());
        assertEquals("UCL-HISTORY", result.applicationNumber());
        assertEquals("UNDER_REVIEW", result.applicationStatus());
        assertEquals(List.of(1, 2), result.cycles().stream().map(c -> c.cycleNumber()).toList());
        for (int i = 0; i < source.size(); i++) {
            var expected = source.get(i);
            var actual = result.cycles().get(i);
            assertEquals(expected.id(), actual.reviewCycleId());
            assertEquals(expected.assignedLoanOfficerUserId(), actual.assignedLoanOfficerUserId());
            assertEquals(expected.status().name(), actual.status());
            assertEquals(expected.startedAt(), actual.startedAt());
            assertEquals(expected.endedAt(), actual.endedAt());
        }
        source.clear();
        assertEquals(2, result.cycles().size());
        assertThrows(UnsupportedOperationException.class, () -> result.cycles().clear());
        verify(applications).findById(id);
        verify(cycles).findByLoanApplicationIdOrderByCycleNumberAsc(id);
        verifyNoMoreInteractions(applications, cycles);
    }

    @Test
    void missingApplicationDoesNotReadCyclesAndErrorsAreUnchanged() {
        var applications = mock(LoanApplicationRepository.class);
        var cycles = mock(LoanReviewCycleRepository.class);
        var service = new QueryApprovalLoanReviewHistoryService(applications, cycles);
        UUID id = UUID.randomUUID();
        assertTrue(service.findHistory(id).isEmpty());
        verifyNoInteractions(cycles);
        var failure = new IllegalStateException("unavailable");
        when(applications.findById(id)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.findHistory(id)));
    }
}
