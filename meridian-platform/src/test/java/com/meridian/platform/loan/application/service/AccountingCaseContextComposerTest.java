package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.AccountingCustomerIdentityPort;
import com.meridian.platform.loan.application.port.out.AccountingCustomerIdentitySnapshot;
import com.meridian.platform.loan.application.port.out.ApprovedOfferApprovalProvenancePort;
import com.meridian.platform.loan.application.port.out.ApprovedOfferApprovalSnapshot;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.StaffActorDirectoryPort;
import com.meridian.platform.loan.application.port.out.StaffActorSummary;
import com.meridian.platform.loan.application.port.out.StaffAssistedContractAcknowledgmentRepository;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ManualDisbursement;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.StaffAssistedContractAcknowledgment;
import com.meridian.platform.loan.testsupport.LoanContractTestData;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountingCaseContextComposerTest {
    private static final UUID APPLICATION_ID = LoanContractTestData.APPLICATION_ID;
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID APPROVER_ID = UUID.randomUUID();
    private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 9, 15, 9, 0);

    private AccountingCustomerIdentityPort customers;
    private ApprovedOfferRepository offers;
    private ApprovedOfferApprovalProvenancePort approvals;
    private StaffAssistedContractAcknowledgmentRepository acknowledgments;
    private StaffActorDirectoryPort actors;
    private AccountingCaseContextComposer composer;
    private LoanApplication application;

    @BeforeEach
    void setUp() {
        customers = mock(AccountingCustomerIdentityPort.class);
        offers = mock(ApprovedOfferRepository.class);
        approvals = mock(ApprovedOfferApprovalProvenancePort.class);
        acknowledgments = mock(StaffAssistedContractAcknowledgmentRepository.class);
        actors = mock(StaffActorDirectoryPort.class);
        composer = new AccountingCaseContextComposer(customers, offers, approvals, acknowledgments, actors);
        application = mock(LoanApplication.class);
        when(application.id()).thenReturn(APPLICATION_ID);
        when(application.customerId()).thenReturn(CUSTOMER_ID);
        when(application.originationChannel()).thenReturn(OriginationChannel.CUSTOMER_DIGITAL);
    }

    @Test
    void preparationStageContainsOnlyCustomerIdentityAndExactApproval() {
        stubCommon();
        var result = composer.compose(application, null, null);
        assertEquals("CUS-001", result.customer().customerNumber());
        assertEquals("Ari Customer", result.customer().fullName());
        assertEquals("0901234567", result.customer().phoneNumber());
        assertEquals(APPROVER_ID, result.handoff().approved().actor().userId());
        assertEquals(APPROVED_AT, result.handoff().approved().at());
        assertNull(result.handoff().contractPrepared());
        assertNull(result.handoff().customerAcknowledgment());
        assertNull(result.handoff().readinessConfirmed());
        assertNull(result.handoff().disbursementConfirmed());
        verify(actors).findByUserIds(Set.of(APPROVER_ID));
    }

    @Test
    void currentPhoneRefreshDoesNotChangeHandoffEvidenceAndContextRemainsNarrow() {
        stubCommon();
        var before = composer.compose(application, LoanContractTestData.ready(), null);
        when(customers.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new AccountingCustomerIdentitySnapshot("CUS-001", "Ari Customer", "0912345678")));
        var after = composer.compose(application, LoanContractTestData.ready(), null);
        assertEquals("0912345678", after.customer().phoneNumber());
        assertEquals(before.handoff(), after.handoff());
        assertEquals(Set.of("customerNumber", "fullName", "phoneNumber"), java.util.Arrays.stream(
                after.customer().getClass().getRecordComponents()).map(c -> c.getName())
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals("CustomerDto[identity=redacted]", after.customer().toString());
        for (String phone : new String[] {null, " "}) {
            when(customers.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                    new AccountingCustomerIdentitySnapshot("CUS-001", "Ari Customer", phone)));
            assertConflict(() -> composer.compose(application, null, null));
        }
    }

    @Test
    void preparedAndDigitalAcknowledgedStagesKeepLaterEventsAbsent() {
        stubCommon();
        var prepared = composer.compose(application, LoanContractTestData.prepared(), null);
        assertEquals(LoanContractTestData.prepared().preparedAt(), prepared.handoff().contractPrepared().at());
        assertNull(prepared.handoff().customerAcknowledgment());

        var acknowledged = composer.compose(application, LoanContractTestData.acknowledged(), null);
        assertEquals("CUSTOMER_SELF_SERVICE", acknowledged.handoff().customerAcknowledgment().mode());
        assertNull(acknowledged.handoff().customerAcknowledgment().recordedBy());
        assertNull(acknowledged.handoff().customerAcknowledgment().evidenceDocumentVersionId());
        assertEquals(LoanContractTestData.acknowledged().acknowledgedAt(),
                acknowledged.handoff().customerAcknowledgment().at());
        assertNull(acknowledged.handoff().readinessConfirmed());
    }

    @Test
    void readyAndDisbursedStagesResolveExactContractAndDisbursementActorsInOneBatch() {
        stubCommon();
        var ready = LoanContractTestData.ready();
        var pending = composer.compose(application, ready, null);
        assertEquals(ready.confirmedByUserId(), pending.handoff().readinessConfirmed().actor().userId());
        assertEquals(ready.confirmedAt(), pending.handoff().readinessConfirmed().at());
        assertNull(pending.handoff().disbursementConfirmed());

        ManualDisbursement disbursement = mock(ManualDisbursement.class);
        UUID confirmer = UUID.randomUUID();
        LocalDateTime at = LocalDateTime.of(2026, 9, 15, 12, 0);
        when(disbursement.loanApplicationId()).thenReturn(APPLICATION_ID);
        when(disbursement.loanContractId()).thenReturn(ready.id());
        when(disbursement.expectedContractVersion()).thenReturn(ready.contractVersion());
        when(disbursement.confirmedByUserId()).thenReturn(confirmer);
        when(disbursement.confirmedAt()).thenReturn(at);
        var completed = composer.compose(application, ready, disbursement);
        assertEquals(confirmer, completed.handoff().disbursementConfirmed().actor().userId());
        assertEquals(at, completed.handoff().disbursementConfirmed().at());
        verify(actors).findByUserIds(Set.of(APPROVER_ID, ready.preparedByUserId(),
                ready.confirmedByUserId(), confirmer));
    }

    @Test
    void staffAssistedEvidenceMustMatchCurrentApplicationCustomerContractVersionAndActor() {
        stubCommon();
        when(application.originationChannel()).thenReturn(OriginationChannel.STAFF_ASSISTED);
        when(application.productCode()).thenReturn(ProductCode.UNSECURED_CONSUMER_LOAN);
        var contract = LoanContractTestData.acknowledged();
        var exact = assisted(contract, APPLICATION_ID, CUSTOMER_ID, contract.contractVersion());
        when(acknowledgments.findByLoanContractIdAndContractVersion(contract.id(), contract.contractVersion()))
                .thenReturn(Optional.of(exact));
        var result = composer.compose(application, contract, null);
        assertEquals("STAFF_RECORDED_CUSTOMER_EVIDENCE", result.handoff().customerAcknowledgment().mode());
        assertEquals(exact.recordedByStaffUserId(), result.handoff().customerAcknowledgment().recordedBy().userId());
        assertEquals(exact.evidenceDocumentVersionId(),
                result.handoff().customerAcknowledgment().evidenceDocumentVersionId());

        when(acknowledgments.findByLoanContractIdAndContractVersion(contract.id(), contract.contractVersion()))
                .thenReturn(Optional.empty());
        assertConflict(() -> composer.compose(application, LoanContractTestData.ready(), null));
        when(acknowledgments.findByLoanContractIdAndContractVersion(contract.id(), contract.contractVersion()))
                .thenReturn(Optional.of(assisted(contract, UUID.randomUUID(), CUSTOMER_ID, contract.contractVersion())));
        assertConflict(() -> composer.compose(application, contract, null));
        when(acknowledgments.findByLoanContractIdAndContractVersion(contract.id(), contract.contractVersion()))
                .thenReturn(Optional.of(assisted(contract, APPLICATION_ID, UUID.randomUUID(), contract.contractVersion())));
        assertConflict(() -> composer.compose(application, contract, null));
        when(acknowledgments.findByLoanContractIdAndContractVersion(contract.id(), contract.contractVersion()))
                .thenReturn(Optional.of(assisted(contract, APPLICATION_ID, CUSTOMER_ID, contract.contractVersion() + 1)));
        assertConflict(() -> composer.compose(application, contract, null));
        when(acknowledgments.findByLoanContractIdAndContractVersion(contract.id(), contract.contractVersion()))
                .thenReturn(Optional.of(new StaffAssistedContractAcknowledgment(
                        UUID.randomUUID(), contract.acknowledgmentRequestId(), APPLICATION_ID, CUSTOMER_ID,
                        UUID.randomUUID(), contract.contractVersion(), UUID.randomUUID(),
                        contract.acknowledgedByUserId(), contract.acknowledgedAt())));
        assertConflict(() -> composer.compose(application, contract, null));
    }

    @Test
    void missingOrContradictoryAuthoritativeEvidenceFailsClosed() {
        assertConflict(() -> composer.compose(application, null, null));
        stubCommon();
        when(customers.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
        assertConflict(() -> composer.compose(application, null, null));
        when(customers.findByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(new AccountingCustomerIdentitySnapshot("CUS-001", "Ari Customer", "0901234567")));
        when(approvals.requireExactApproval(APPLICATION_ID, APPROVED_AT))
                .thenReturn(new ApprovedOfferApprovalSnapshot(APPROVER_ID, APPROVED_AT.plusSeconds(1)));
        assertConflict(() -> composer.compose(application, null, null));
        when(approvals.requireExactApproval(APPLICATION_ID, APPROVED_AT))
                .thenReturn(new ApprovedOfferApprovalSnapshot(APPROVER_ID, APPROVED_AT));
        when(actors.findByUserIds(anySet())).thenReturn(Map.of());
        assertConflict(() -> composer.compose(application, null, null));
        stubCommon();
        ApprovedOffer wrongOffer = mock(ApprovedOffer.class);
        when(wrongOffer.loanApplicationId()).thenReturn(APPLICATION_ID);
        when(wrongOffer.status()).thenReturn(ApprovedOfferStatus.ACCEPTED);
        when(wrongOffer.generatedAt()).thenReturn(APPROVED_AT);
        when(wrongOffer.id()).thenReturn(UUID.randomUUID());
        when(offers.findByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.of(wrongOffer));
        assertConflict(() -> composer.compose(application, LoanContractTestData.prepared(), null));
    }

    private void stubCommon() {
        when(customers.findByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(new AccountingCustomerIdentitySnapshot("CUS-001", "Ari Customer", "0901234567")));
        ApprovedOffer offer = mock(ApprovedOffer.class);
        when(offer.id()).thenReturn(LoanContractTestData.prepared().approvedOfferId());
        when(offer.loanApplicationId()).thenReturn(APPLICATION_ID);
        when(offer.status()).thenReturn(ApprovedOfferStatus.ACCEPTED);
        when(offer.generatedAt()).thenReturn(APPROVED_AT);
        when(offers.findByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.of(offer));
        when(approvals.requireExactApproval(APPLICATION_ID, APPROVED_AT))
                .thenReturn(new ApprovedOfferApprovalSnapshot(APPROVER_ID, APPROVED_AT));
        when(actors.findByUserIds(anySet())).thenAnswer(invocation -> {
            Set<UUID> requested = invocation.getArgument(0);
            return requested.stream().collect(java.util.stream.Collectors.toMap(id -> id,
                    id -> new StaffActorSummary(id, "Mina Accounting", "mina@meridian.local")));
        });
    }

    private static StaffAssistedContractAcknowledgment assisted(
            com.meridian.platform.loan.domain.model.LoanContract contract,
            UUID applicationId, UUID customerId, int version
    ) {
        return new StaffAssistedContractAcknowledgment(UUID.randomUUID(), contract.acknowledgmentRequestId(),
                applicationId, customerId, contract.id(), version, UUID.randomUUID(),
                contract.acknowledgedByUserId(), contract.acknowledgedAt());
    }

    private static void assertConflict(org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(BusinessStateConflictException.class, operation);
        assertEquals("SYSTEM_STATE_CONFLICT", failure.getErrorCode());
    }
}
