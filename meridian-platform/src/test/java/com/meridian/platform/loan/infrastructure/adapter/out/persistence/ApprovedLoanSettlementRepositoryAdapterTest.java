package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.ApprovedLoanSettlementSaveOutcome;
import com.meridian.platform.loan.domain.model.ApprovedLoanSettlement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovedLoanSettlementRepositoryAdapterTest {

    @Mock JpaApprovedLoanSettlementRepository repository;

    private ApprovedLoanSettlementRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ApprovedLoanSettlementRepositoryAdapter(repository);
    }

    @Test
    void delegatesOperationSpecificRequestLock() {
        UUID requestId = UUID.randomUUID();

        adapter.acquireApprovalRequestLock(requestId);

        verify(repository).acquireApprovalRequestLock(requestId);
    }

    @Test
    void reportsInsertedAndExistingRequestEvidence() {
        ApprovedLoanSettlement attempted = evidence();
        when(repository.insertIfNoConflict(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(1);

        ApprovedLoanSettlementSaveOutcome.Inserted inserted = assertInstanceOf(
                ApprovedLoanSettlementSaveOutcome.Inserted.class,
                adapter.save(attempted)
        );
        assertEquals(attempted, inserted.settlement());

        when(repository.insertIfNoConflict(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(0);
        when(repository.findByRequestId(attempted.requestId()))
                .thenReturn(Optional.of(new ApprovedLoanSettlementJpaEntity(attempted)));

        ApprovedLoanSettlementSaveOutcome.ExistingRequest replay = assertInstanceOf(
                ApprovedLoanSettlementSaveOutcome.ExistingRequest.class,
                adapter.save(attempted)
        );
        assertEquals(attempted, replay.settlement());
    }

    @Test
    void distinguishesAccountTransactionAndEvidenceIdentityConflicts() {
        ApprovedLoanSettlement attempted = evidence();
        ApprovedLoanSettlementJpaEntity entity =
                new ApprovedLoanSettlementJpaEntity(attempted);

        when(repository.findByLoanAccountId(attempted.loanAccountId()))
                .thenReturn(Optional.of(entity));
        assertConflict(
                ApprovedLoanSettlementSaveOutcome.ConflictKind.LOAN_ACCOUNT,
                attempted
        );

        when(repository.findByLoanAccountId(attempted.loanAccountId()))
                .thenReturn(Optional.empty());
        when(repository.findByRepaymentTransactionId(
                attempted.repaymentTransactionId())).thenReturn(Optional.of(entity));
        assertConflict(
                ApprovedLoanSettlementSaveOutcome.ConflictKind.REPAYMENT_TRANSACTION,
                attempted
        );

        when(repository.findByRepaymentTransactionId(
                attempted.repaymentTransactionId())).thenReturn(Optional.empty());
        when(repository.findById(attempted.id())).thenReturn(Optional.of(entity));
        assertConflict(
                ApprovedLoanSettlementSaveOutcome.ConflictKind.SETTLEMENT_ID,
                attempted
        );
    }

    private void assertConflict(
            ApprovedLoanSettlementSaveOutcome.ConflictKind expected,
            ApprovedLoanSettlement attempted
    ) {
        ApprovedLoanSettlementSaveOutcome.Conflict conflict = assertInstanceOf(
                ApprovedLoanSettlementSaveOutcome.Conflict.class,
                adapter.save(attempted)
        );
        assertEquals(expected, conflict.kind());
    }

    private static ApprovedLoanSettlement evidence() {
        return new ApprovedLoanSettlement(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                UUID.randomUUID(),
                LocalDateTime.of(2026, 8, 9, 10, 0)
        );
    }
}
