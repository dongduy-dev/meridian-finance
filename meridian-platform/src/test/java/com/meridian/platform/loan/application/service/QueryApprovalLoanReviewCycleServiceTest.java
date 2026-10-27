package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanReviewCycleStatus;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryApprovalLoanReviewCycleServiceTest {
    @Test
    void returnsExactCycleAndNullableLegacyAssignmentUsingOnlyTheNonLockingRead() {
        var repository = mock(LoanReviewCycleRepository.class);
        var service = new QueryApprovalLoanReviewCycleService(repository);
        UUID application = UUID.randomUUID();
        UUID cycle = UUID.randomUUID();
        for (UUID officer : new UUID[]{UUID.randomUUID(), null}) {
            when(repository.findActiveByLoanApplicationId(application)).thenReturn(Optional.of(
                    new LoanApplicationReviewCycle(cycle, application, 3, officer, LoanReviewCycleStatus.ACTIVE,
                            LocalDateTime.of(2026, 10, 1, 8, 0), null)));
            var result = service.findActiveReviewCycle(application).orElseThrow();
            assertEquals(cycle, result.reviewCycleId());
            assertEquals(officer, result.assignedLoanOfficerUserId());
        }
        verify(repository, times(2)).findActiveByLoanApplicationId(application);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void absenceAndRepositoryFailurePropagateWithoutOtherReadsOrLocks() {
        var repository = mock(LoanReviewCycleRepository.class);
        var service = new QueryApprovalLoanReviewCycleService(repository);
        UUID application = UUID.randomUUID();
        assertTrue(service.findActiveReviewCycle(application).isEmpty());
        var failure = new IllegalStateException("query failed");
        when(repository.findActiveByLoanApplicationId(application)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.findActiveReviewCycle(application)));
        verify(repository, times(2)).findActiveByLoanApplicationId(application);
        verifyNoMoreInteractions(repository);
    }
}
