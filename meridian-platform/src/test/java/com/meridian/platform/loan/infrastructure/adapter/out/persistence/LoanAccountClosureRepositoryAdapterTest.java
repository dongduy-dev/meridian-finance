package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanAccountClosureSaveOutcome;
import com.meridian.platform.loan.domain.model.LoanAccountClosure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanAccountClosureRepositoryAdapterTest {

    @Mock JpaLoanAccountClosureRepository repository;

    private LoanAccountClosureRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LoanAccountClosureRepositoryAdapter(repository);
    }

    @Test
    void delegatesOperationSpecificRequestLock() {
        UUID requestId = UUID.randomUUID();

        adapter.acquireClosureRequestLock(requestId);

        verify(repository).acquireClosureRequestLock(requestId);
    }

    @Test
    void reportsInsertedAndExistingRequestEvidence() {
        LoanAccountClosure attempted = evidence();
        when(repository.insertIfNoConflict(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(1);

        LoanAccountClosureSaveOutcome.Inserted inserted = assertInstanceOf(
                LoanAccountClosureSaveOutcome.Inserted.class,
                adapter.save(attempted)
        );
        assertEquals(attempted, inserted.closure());

        when(repository.insertIfNoConflict(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(0);
        when(repository.findByRequestId(attempted.requestId()))
                .thenReturn(Optional.of(new LoanAccountClosureJpaEntity(attempted)));

        LoanAccountClosureSaveOutcome.ExistingRequest replay = assertInstanceOf(
                LoanAccountClosureSaveOutcome.ExistingRequest.class,
                adapter.save(attempted)
        );
        assertEquals(attempted, replay.closure());
    }

    @Test
    void distinguishesAccountAndEvidenceIdentityConflicts() {
        LoanAccountClosure attempted = evidence();
        LoanAccountClosureJpaEntity entity =
                new LoanAccountClosureJpaEntity(attempted);

        when(repository.findByLoanAccountId(attempted.loanAccountId()))
                .thenReturn(Optional.of(entity));
        assertConflict(LoanAccountClosureSaveOutcome.ConflictKind.LOAN_ACCOUNT,
                attempted);

        when(repository.findByLoanAccountId(attempted.loanAccountId()))
                .thenReturn(Optional.empty());
        when(repository.findById(attempted.id())).thenReturn(Optional.of(entity));
        assertConflict(LoanAccountClosureSaveOutcome.ConflictKind.CLOSURE_ID,
                attempted);
    }

    private void assertConflict(
            LoanAccountClosureSaveOutcome.ConflictKind expected,
            LoanAccountClosure attempted
    ) {
        LoanAccountClosureSaveOutcome.Conflict conflict = assertInstanceOf(
                LoanAccountClosureSaveOutcome.Conflict.class,
                adapter.save(attempted)
        );
        assertEquals(expected, conflict.kind());
    }

    private static LoanAccountClosure evidence() {
        return new LoanAccountClosure(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.of(2026, 8, 9, 10, 0)
        );
    }
}
