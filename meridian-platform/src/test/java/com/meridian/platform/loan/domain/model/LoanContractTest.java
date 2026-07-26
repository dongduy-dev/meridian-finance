package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LoanContractTest {
    @Test void preparedSnapshotIsImmutableAndReconciled() {
        ArrayList<LoanContractRepaymentItem> source = new ArrayList<>(List.of(item()));
        LoanContract contract = prepared(source);
        source.clear();
        assertEquals(1, contract.repaymentItems().size());
        assertThrows(UnsupportedOperationException.class, () -> contract.repaymentItems().clear());
        assertTrue(contract.disbursementBankAccount().toString().contains("redacted"));
        assertFalse(contract.disbursementBankAccount().toString().contains("cipher"));
    }

    @Test void acknowledgmentAndReadinessAreOneWay() {
        LoanContract contract = prepared(List.of(item()));
        LocalDateTime acknowledgedAt = contract.preparedAt().plusSeconds(1);
        LoanContract acknowledged = contract.acknowledge(UUID.randomUUID(), UUID.randomUUID(), acknowledgedAt);
        LoanContract ready = acknowledged.confirmReady(UUID.randomUUID(), UUID.randomUUID(), acknowledgedAt.plusSeconds(1));
        assertEquals(LoanContractStatus.READY_FOR_DISBURSEMENT, ready.status());
        assertThrows(BusinessStateConflictException.class,
                () -> ready.acknowledge(UUID.randomUUID(), UUID.randomUUID(), acknowledgedAt));
        assertThrows(BusinessStateConflictException.class,
                () -> ready.supersede(UUID.randomUUID(), acknowledgedAt));
    }

    @Test void supersessionRequiresFreshAcknowledgmentOnNewVersion() {
        LoanContract first = prepared(List.of(item()));
        assertEquals(LoanContractStatus.SUPERSEDED,
                first.supersede(UUID.randomUUID(), LocalDateTime.now()).status());
        LoanContract second = LoanContract.prepared(UUID.randomUUID(), first.loanApplicationId(),
                first.approvedOfferId(), "MCT-2", 2, terms(), List.of(item()), account(), UUID.randomUUID(),
                1, ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH, UUID.randomUUID(),
                LocalDateTime.now(), first.id());
        assertEquals(LoanContractStatus.PREPARED, second.status());
        assertNull(second.acknowledgedAt());
    }
    @Test void rejectsEveryPartialLifecycleEvidenceGroup() {
        LoanContract base = prepared(List.of(item()));
        UUID request = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        LocalDateTime afterPreparation = base.preparedAt().plusMinutes(1);

        for (int mask = 1; mask < 7; mask++) {
            int partial = mask;
            assertThrows(BusinessStateConflictException.class, () -> rehydrate(base, LoanContractStatus.SUPERSEDED,
                    (partial & 1) == 0 ? null : request,
                    (partial & 2) == 0 ? null : actor,
                    (partial & 4) == 0 ? null : afterPreparation,
                    null, null, null, actor, afterPreparation.plusMinutes(1)));
            assertThrows(BusinessStateConflictException.class, () -> rehydrate(base, LoanContractStatus.READY_FOR_DISBURSEMENT,
                    request, actor, afterPreparation,
                    (partial & 1) == 0 ? null : UUID.randomUUID(),
                    (partial & 2) == 0 ? null : UUID.randomUUID(),
                    (partial & 4) == 0 ? null : afterPreparation.plusMinutes(1),
                    null, null));
        }

        assertThrows(BusinessStateConflictException.class, () -> rehydrate(base, LoanContractStatus.SUPERSEDED,
                null, null, null, null, null, null, actor, null));
        assertThrows(BusinessStateConflictException.class, () -> rehydrate(base, LoanContractStatus.SUPERSEDED,
                null, null, null, null, null, null, null, afterPreparation));
    }

    @Test void rejectsRetrogradeLifecycleTimestampsAndAllowsBothSupersessionOrigins() {
        LoanContract base = prepared(List.of(item()));
        UUID request = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        assertThrows(BusinessStateConflictException.class,
                () -> base.acknowledge(request, actor, base.preparedAt().minusNanos(1)));
        assertThrows(BusinessStateConflictException.class,
                () -> base.supersede(actor, base.preparedAt().minusNanos(1)));

        LoanContract acknowledged = base.acknowledge(request, actor, base.preparedAt().plusMinutes(1));
        assertThrows(BusinessStateConflictException.class,
                () -> acknowledged.confirmReady(UUID.randomUUID(), UUID.randomUUID(),
                        acknowledged.acknowledgedAt().minusNanos(1)));
        assertThrows(BusinessStateConflictException.class,
                () -> acknowledged.supersede(UUID.randomUUID(), acknowledged.acknowledgedAt().minusNanos(1)));

        assertEquals(LoanContractStatus.SUPERSEDED,
                base.supersede(UUID.randomUUID(), base.preparedAt()).status());
        assertEquals(LoanContractStatus.SUPERSEDED,
                acknowledged.supersede(UUID.randomUUID(), acknowledged.acknowledgedAt()).status());
    }

    private static LoanContract rehydrate(
            LoanContract base, LoanContractStatus status,
            UUID ackRequest, UUID ackActor, LocalDateTime ackAt,
            UUID confirmRequest, UUID confirmActor, LocalDateTime confirmAt,
            UUID supersedeActor, LocalDateTime supersedeAt
    ) {
        return new LoanContract(base.id(), base.loanApplicationId(), base.approvedOfferId(),
                base.contractReference(), base.contractVersion(), status, base.financialTerms(),
                base.repaymentItems(), base.disbursementBankAccount(), base.preparationRequestId(),
                base.expectedPreviousVersion(), base.supersessionReason(), base.preparedByUserId(),
                base.preparedAt(), ackRequest, ackActor, ackAt, confirmRequest, confirmActor, confirmAt,
                base.supersedesContractId(), supersedeActor, supersedeAt);
    }

    @Test void rejectsRepaymentMismatch() {
        LoanContractRepaymentItem wrong = new LoanContractRepaymentItem(UUID.randomUUID(), UUID.randomUUID(),
                1, money(900), money(100), money(0), money(1000));
        assertThrows(BusinessStateConflictException.class, () -> prepared(List.of(wrong)));
    }

    @Test void applicationMovesOnlyFromContractPendingToDisbursementPending() {
        LoanApplicationTransitionResult result = application(LoanApplicationStatus.CONTRACT_PENDING)
                .confirmDisbursementReadiness();
        assertEquals(LoanApplicationStatus.DISBURSEMENT_PENDING, result.loanApplication().status());
        assertEquals(LoanApplicationTransitionAction.CONFIRM_DISBURSEMENT_READINESS,
                result.facts().getFirst().action());
        assertThrows(BusinessStateConflictException.class,
                () -> application(LoanApplicationStatus.APPROVED).confirmDisbursementReadiness());
    }

    private static LoanContract prepared(List<LoanContractRepaymentItem> items) {
        return LoanContract.prepared(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "MCT-1", 1,
                terms(), items, account(), UUID.randomUUID(), null, null, UUID.randomUUID(), LocalDateTime.now(), null);
    }
    private static ApprovedOfferFinancialTerms terms() {
        return new ApprovedOfferFinancialTerms(money(1000), 1, InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.100000"), money(100), money(0), money(1100), RepaymentMethod.ON_SALARY_DATE);
    }
    private static LoanContractRepaymentItem item() {
        return new LoanContractRepaymentItem(UUID.randomUUID(), UUID.randomUUID(), 1,
                money(1000), money(100), money(0), money(1100));
    }
    private static ProtectedDisbursementBankAccount account() {
        return new ProtectedDisbursementBankAccount(UUID.randomUUID(), UUID.randomUUID(), "VCB", "Vietcombank",
                "MERIDIAN CUSTOMER", "5678", true, true, LocalDateTime.now(), "AES-256-GCM", "v1",
                new byte[12], new byte[]{1}, "DISBURSEMENT_ACCOUNT_V1");
    }
    private static LoanApplication application(LoanApplicationStatus status) {
        return new LoanApplication(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SA-1",
                ProductCode.SALARY_ADVANCE, ProductType.SALARY_BASED, status, money(1000), 1, LocalDateTime.now());
    }
    private static BigDecimal money(long amount) { return BigDecimal.valueOf(amount).setScale(2); }
}
