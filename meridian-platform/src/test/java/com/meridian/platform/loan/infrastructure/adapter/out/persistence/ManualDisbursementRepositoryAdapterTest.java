package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.ManualDisbursementSaveOutcome;
import com.meridian.platform.loan.domain.model.ManualDisbursement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualDisbursementRepositoryAdapterTest {

    private static final LocalDateTime CONFIRMED_AT =
            LocalDateTime.of(2026, 7, 27, 10, 15);

    @Mock
    private JpaManualDisbursementRepository jpaRepository;

    private ManualDisbursementRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ManualDisbursementRepositoryAdapter(jpaRepository);
    }

    @Test
    void reportsInsertedEvidence() {
        ManualDisbursement attempted = evidence("SAFE-INSERT");
        arrangeInsertCount(1);

        ManualDisbursementSaveOutcome.Inserted outcome = assertInstanceOf(
                ManualDisbursementSaveOutcome.Inserted.class,
                adapter.save(attempted)
        );

        assertEquals(attempted, outcome.manualDisbursement());
    }

    @Test
    void requestConflictWinsWhenEveryLowerPriorityIdentityAlsoConflicts() {
        ManualDisbursement attempted = evidence("SAFE-ATTEMPT");
        ManualDisbursement existing = new ManualDisbursement(
                attempted.id(),
                attempted.loanApplicationId(),
                attempted.loanContractId(),
                attempted.loanAccountId(),
                attempted.requestId(),
                7,
                attempted.externalTransferReference(),
                money("2700"),
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 8, 20),
                UUID.randomUUID(),
                CONFIRMED_AT.minusHours(1)
        );
        ManualDisbursementJpaEntity entity = new ManualDisbursementJpaEntity(existing);
        when(jpaRepository.findByRequestId(attempted.requestId()))
                .thenReturn(Optional.of(entity));

        ManualDisbursementSaveOutcome.ExistingRequest outcome = assertInstanceOf(
                ManualDisbursementSaveOutcome.ExistingRequest.class,
                adapter.save(attempted)
        );

        assertEquals(existing, outcome.manualDisbursement());
    }

    @Test
    void distinguishesEveryNonRequestUniqueConflict() {
        assertConflict(
                ManualDisbursementSaveOutcome.ConflictKind.LOAN_APPLICATION,
                (attempted, entity) -> when(jpaRepository.findByLoanApplicationId(
                        attempted.loanApplicationId())).thenReturn(Optional.of(entity))
        );
        assertConflict(
                ManualDisbursementSaveOutcome.ConflictKind.LOAN_CONTRACT,
                (attempted, entity) -> when(jpaRepository.findByLoanContractId(
                        attempted.loanContractId())).thenReturn(Optional.of(entity))
        );
        assertConflict(
                ManualDisbursementSaveOutcome.ConflictKind.LOAN_ACCOUNT,
                (attempted, entity) -> when(jpaRepository.findByLoanAccountId(
                        attempted.loanAccountId())).thenReturn(Optional.of(entity))
        );
        assertConflict(
                ManualDisbursementSaveOutcome.ConflictKind.EXTERNAL_TRANSFER_REFERENCE,
                (attempted, entity) -> when(jpaRepository.findByExternalTransferReference(
                        attempted.externalTransferReference())).thenReturn(Optional.of(entity))
        );
        assertConflict(
                ManualDisbursementSaveOutcome.ConflictKind.DISBURSEMENT_ID,
                (attempted, entity) -> when(jpaRepository.findById(
                        attempted.id())).thenReturn(Optional.of(entity))
        );
    }

    @Test
    void reportsUnresolvedZeroRowInsertWithoutClaimingSuccess() {
        ManualDisbursementSaveOutcome outcome = adapter.save(evidence("SAFE-UNRESOLVED"));

        assertInstanceOf(ManualDisbursementSaveOutcome.UnresolvedConflict.class, outcome);
    }

    @Test
    void outcomeStringsNeverRevealCanonicalTransferReference() {
        ManualDisbursement evidence = evidence("PRIVATE-REFERENCE-42");
        ManualDisbursementSaveOutcome inserted =
                new ManualDisbursementSaveOutcome.Inserted(evidence);
        ManualDisbursementSaveOutcome existing =
                new ManualDisbursementSaveOutcome.ExistingRequest(evidence);
        ManualDisbursementSaveOutcome conflict = new ManualDisbursementSaveOutcome.Conflict(
                ManualDisbursementSaveOutcome.ConflictKind.EXTERNAL_TRANSFER_REFERENCE
        );

        assertFalse(inserted.toString().contains(evidence.externalTransferReference()));
        assertFalse(existing.toString().contains(evidence.externalTransferReference()));
        assertFalse(conflict.toString().contains(evidence.externalTransferReference()));
    }

    private void assertConflict(
            ManualDisbursementSaveOutcome.ConflictKind expected,
            ConflictArrangement arrangement
    ) {
        ManualDisbursement attempted = evidence("SAFE-" + expected.name());
        arrangement.arrange(attempted, new ManualDisbursementJpaEntity(attempted));

        ManualDisbursementSaveOutcome.Conflict outcome = assertInstanceOf(
                ManualDisbursementSaveOutcome.Conflict.class,
                adapter.save(attempted)
        );

        assertEquals(expected, outcome.kind());
    }

    private void arrangeInsertCount(int count) {
        when(jpaRepository.insertIfNoConflict(
                any(), any(), any(), any(), any(), anyInt(), anyString(),
                any(BigDecimal.class), any(LocalDate.class), any(LocalDate.class),
                any(), any(LocalDateTime.class)
        )).thenReturn(count);
    }

    private static ManualDisbursement evidence(String reference) {
        return new ManualDisbursement(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                reference,
                money("1000"),
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 8, 27),
                UUID.randomUUID(),
                CONFIRMED_AT
        );
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    @FunctionalInterface
    private interface ConflictArrangement {
        void arrange(
                ManualDisbursement attempted,
                ManualDisbursementJpaEntity existing
        );
    }
}
