package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.RecordAssistedLoanContractAcknowledgmentUseCase;
import com.meridian.platform.loan.application.port.out.*;
import com.meridian.platform.loan.domain.model.*;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordAssistedLoanContractAcknowledgmentServiceTest {

    @Mock LoanApplicationRepository applications;
    @Mock LoanContractRepository contracts;
    @Mock StaffAssistedContractAcknowledgmentRepository acknowledgments;
    @Mock LoanAssistedActionEvidencePort evidence;
    @Mock CurrentUserProvider users;
    @Mock BusinessAuditPublisher audit;
    private RecordAssistedLoanContractAcknowledgmentService service;
    private final UUID requestId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();
    private final UUID evidenceVersionId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RecordAssistedLoanContractAcknowledgmentService(
                applications, contracts, acknowledgments, evidence, users, audit,
                Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void recordsExactEvidenceAndStaffActorWhileCustomerRemainsSubject() {
        LoanApplication application = application(OriginationChannel.STAFF_ASSISTED);
        LoanContract prepared = mock(LoanContract.class);
        LoanContract acknowledged = mock(LoanContract.class);
        when(users.currentUser()).thenReturn(accountingOfficer());
        when(acknowledgments.findByAcknowledgmentRequestId(requestId)).thenReturn(Optional.empty());
        when(contracts.findByAcknowledgmentRequestId(requestId)).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application));
        when(contracts.findCurrentByApplicationIdForUpdate(applicationId)).thenReturn(Optional.of(prepared));
        when(prepared.id()).thenReturn(contractId);
        when(prepared.contractVersion()).thenReturn(2);
        when(prepared.status()).thenReturn(LoanContractStatus.PREPARED);
        when(prepared.acknowledge(eq(requestId), eq(actorId), any())).thenReturn(acknowledged);
        when(contracts.save(acknowledged)).thenReturn(acknowledged);
        when(acknowledged.id()).thenReturn(contractId);
        when(acknowledged.status()).thenReturn(LoanContractStatus.ACKNOWLEDGED);
        when(acknowledgments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LoanContract result = service.record(command());

        assertSame(acknowledged, result);
        verify(evidence).requireCurrentContractEvidence(applicationId, contractId, 2, evidenceVersionId);
        verify(acknowledgments).save(argThat(record ->
                record.customerId().equals(application.customerId())
                        && record.recordedByStaffUserId().equals(actorId)
                        && record.evidenceDocumentVersionId().equals(evidenceVersionId)));
        verify(audit).publish(any());
    }

    @Test
    void recordsAssistedCollateralAcknowledgmentAgainstExactCurrentVersion() {
        LoanApplication application = application(
                OriginationChannel.STAFF_ASSISTED, ProductCode.COLLATERAL_LOAN,
                LoanApplicationStatus.CONTRACT_PENDING);
        LoanContract prepared = mock(LoanContract.class);
        LoanContract acknowledged = mock(LoanContract.class);
        prepareContract(application, prepared);
        when(prepared.status()).thenReturn(LoanContractStatus.PREPARED);
        when(prepared.acknowledge(eq(requestId), eq(actorId), any())).thenReturn(acknowledged);
        when(contracts.save(acknowledged)).thenReturn(acknowledged);
        when(acknowledged.id()).thenReturn(contractId);
        when(acknowledged.status()).thenReturn(LoanContractStatus.ACKNOWLEDGED);
        when(acknowledgments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertSame(acknowledged, service.record(command()));

        verify(evidence).requireCurrentContractEvidence(applicationId, contractId, 2, evidenceVersionId);
        verify(acknowledgments).save(argThat(record -> record.customerId().equals(application.customerId())
                && record.contractVersion() == 2
                && record.recordedByStaffUserId().equals(actorId)));
        verify(audit).publish(any());
    }

    @Test
    void rejectsCustomerDigitalChannelBeforeContractMutation() {
        when(users.currentUser()).thenReturn(accountingOfficer());
        when(acknowledgments.findByAcknowledgmentRequestId(requestId)).thenReturn(Optional.empty());
        when(contracts.findByAcknowledgmentRequestId(requestId)).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(applicationId)).thenReturn(Optional.of(
                application(OriginationChannel.CUSTOMER_DIGITAL)));

        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.record(command()));

        assertEquals("ASSISTED_ACTION_NOT_ALLOWED", error.getErrorCode());
        verify(contracts, never()).save(any());
        verifyNoInteractions(evidence);
    }

    @Test
    void salaryAdvanceCannotBeRepresentedAsAssistedAndWrongApplicationStateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> application(
                OriginationChannel.STAFF_ASSISTED, ProductCode.SALARY_ADVANCE,
                LoanApplicationStatus.CONTRACT_PENDING));

        when(users.currentUser()).thenReturn(accountingOfficer());
        when(acknowledgments.findByAcknowledgmentRequestId(requestId)).thenReturn(Optional.empty());
        when(contracts.findByAcknowledgmentRequestId(requestId)).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application(
                OriginationChannel.STAFF_ASSISTED, ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.DISBURSEMENT_PENDING)));
        assertEquals("INVALID_APPLICATION_STATE", assertThrows(BusinessStateConflictException.class,
                () -> service.record(command())).getErrorCode());
        verifyNoInteractions(evidence);
    }

    @Test
    void rejectsCustomerActorStaffWithCustomerIdentityMissingPermissionNearMissAndWrongRole() {
        AuthenticatedUser customer = new AuthenticatedUser(actorId, "customer@meridian.test", "CUSTOMER",
                UUID.randomUUID(), Set.of("CUSTOMER"), Set.of("loan:contract:acknowledge:staff"));
        AuthenticatedUser staffWithCustomer = new AuthenticatedUser(actorId, "staff@meridian.test", "STAFF",
                UUID.randomUUID(), Set.of("ACCOUNTING_OFFICER"), Set.of("loan:contract:acknowledge:staff"));
        AuthenticatedUser missingPermission = staff(Set.of("ACCOUNTING_OFFICER"), Set.of("loan:read"));
        AuthenticatedUser nearMissPermission = staff(
                Set.of("ACCOUNTING_OFFICER"), Set.of("loan:contract:acknowledge:staff.extra"));
        AuthenticatedUser wrongRole = staff(Set.of("LOAN_OFFICER"), Set.of("loan:contract:acknowledge:staff"));

        for (AuthenticatedUser invalid : new AuthenticatedUser[] {
                customer, staffWithCustomer, missingPermission, nearMissPermission, wrongRole
        }) {
            reset(users, applications, contracts, acknowledgments, evidence, audit);
            when(users.currentUser()).thenReturn(invalid);
            assertThrows(AuthorizationException.class, () -> service.record(command()));
            verifyNoInteractions(applications, contracts, acknowledgments, evidence, audit);
        }
    }

    @Test
    void rejectsMissingContractStaleIdentityAndNonPreparedContract() {
        LoanApplication application = application(OriginationChannel.STAFF_ASSISTED);
        prepareBeforeContract(application);
        when(contracts.findCurrentByApplicationIdForUpdate(applicationId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.record(command()));

        LoanContract other = mock(LoanContract.class);
        reset(applications, contracts, acknowledgments);
        prepareBeforeContract(application);
        when(contracts.findCurrentByApplicationIdForUpdate(applicationId)).thenReturn(Optional.of(other));
        when(other.id()).thenReturn(UUID.randomUUID());
        assertEquals("CONTRACT_VERSION_STALE", assertThrows(BusinessStateConflictException.class,
                () -> service.record(command())).getErrorCode());

        LoanContract notPrepared = mock(LoanContract.class);
        reset(applications, contracts, acknowledgments);
        prepareContract(application, notPrepared);
        when(notPrepared.status()).thenReturn(LoanContractStatus.SUPERSEDED);
        assertEquals("CONTRACT_ACKNOWLEDGMENT_NOT_ALLOWED", assertThrows(BusinessStateConflictException.class,
                () -> service.record(command())).getErrorCode());
        verifyNoInteractions(evidence);
    }

    @Test
    void evidenceFailurePreventsContractAndDurableAcknowledgmentMutation() {
        LoanApplication application = application(OriginationChannel.STAFF_ASSISTED);
        LoanContract prepared = mock(LoanContract.class);
        prepareContract(application, prepared);
        when(prepared.status()).thenReturn(LoanContractStatus.PREPARED);
        doThrow(new BusinessStateConflictException(
                "STALE_DOCUMENT_VERSION", "Evidence is no longer current."))
                .when(evidence).requireCurrentContractEvidence(
                        applicationId, contractId, 2, evidenceVersionId);

        assertEquals("STALE_DOCUMENT_VERSION", assertThrows(BusinessStateConflictException.class,
                () -> service.record(command())).getErrorCode());

        verify(contracts, never()).save(any());
        verify(acknowledgments, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void competingAcknowledgmentForSameContractVersionFailsBeforeEvidenceConsumption() {
        LoanApplication application = application(OriginationChannel.STAFF_ASSISTED);
        LoanContract prepared = mock(LoanContract.class);
        prepareContract(application, prepared);
        when(acknowledgments.findByLoanContractIdAndContractVersion(contractId, 2))
                .thenReturn(Optional.of(new StaffAssistedContractAcknowledgment(
                        UUID.randomUUID(), UUID.randomUUID(), applicationId, application.customerId(),
                        contractId, 2, UUID.randomUUID(), actorId,
                        LocalDateTime.of(2026, 9, 22, 0, 0))));

        assertEquals("CONTRACT_ACKNOWLEDGMENT_NOT_ALLOWED", assertThrows(BusinessStateConflictException.class,
                () -> service.record(command())).getErrorCode());
        verifyNoInteractions(evidence);
        verify(contracts, never()).save(any());
    }

    @Test
    void exactReplayReturnsRecordedContractWithoutRepeatingEffects() {
        LoanContract acknowledged = mock(LoanContract.class);
        StaffAssistedContractAcknowledgment recorded = new StaffAssistedContractAcknowledgment(
                UUID.randomUUID(), requestId, applicationId, UUID.randomUUID(), contractId, 2,
                evidenceVersionId, actorId, LocalDateTime.of(2026, 9, 22, 0, 0));
        when(users.currentUser()).thenReturn(accountingOfficer());
        when(acknowledgments.findByAcknowledgmentRequestId(requestId)).thenReturn(Optional.of(recorded));
        when(contracts.findById(contractId)).thenReturn(Optional.of(acknowledged));

        assertSame(acknowledged, service.record(command()));

        verify(applications, never()).acquireWorkflowLock(any());
        verifyNoInteractions(evidence, audit);
        verify(contracts, never()).save(any());
    }

    @Test
    void replayRequestIdentityWithDifferentEvidenceFailsClosed() {
        StaffAssistedContractAcknowledgment recorded = new StaffAssistedContractAcknowledgment(
                UUID.randomUUID(), requestId, applicationId, UUID.randomUUID(), contractId, 2,
                UUID.randomUUID(), actorId, LocalDateTime.of(2026, 9, 22, 0, 0));
        when(users.currentUser()).thenReturn(accountingOfficer());
        when(acknowledgments.findByAcknowledgmentRequestId(requestId)).thenReturn(Optional.of(recorded));

        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.record(command()));

        assertEquals("IDEMPOTENCY_KEY_REUSED", error.getErrorCode());
        verifyNoInteractions(evidence, audit);
    }

    private RecordAssistedLoanContractAcknowledgmentUseCase.Command command() {
        return new RecordAssistedLoanContractAcknowledgmentUseCase.Command(
                requestId, applicationId, contractId, 2, evidenceVersionId);
    }

    private LoanApplication application(OriginationChannel channel) {
        return application(channel, ProductCode.UNSECURED_CONSUMER_LOAN, LoanApplicationStatus.CONTRACT_PENDING);
    }

    private LoanApplication application(
            OriginationChannel channel, ProductCode productCode, LoanApplicationStatus status
    ) {
        return new LoanApplication(applicationId, UUID.randomUUID(), UUID.randomUUID(), "UCL-1",
                productCode, productCode == ProductCode.COLLATERAL_LOAN ? ProductType.SECURED : ProductType.UNSECURED,
                channel, status, BigDecimal.valueOf(5_000_000), 6,
                LocalDateTime.of(2026, 9, 20, 0, 0));
    }

    private AuthenticatedUser accountingOfficer() {
        return staff(Set.of("ACCOUNTING_OFFICER"), Set.of("loan:contract:acknowledge:staff"));
    }

    private AuthenticatedUser staff(Set<String> roles, Set<String> permissions) {
        return new AuthenticatedUser(actorId, "accounting@meridian.test", "STAFF", null, roles, permissions);
    }

    private void prepareBeforeContract(LoanApplication application) {
        when(users.currentUser()).thenReturn(accountingOfficer());
        when(acknowledgments.findByAcknowledgmentRequestId(requestId)).thenReturn(Optional.empty());
        when(contracts.findByAcknowledgmentRequestId(requestId)).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application));
    }

    private void prepareContract(LoanApplication application, LoanContract prepared) {
        prepareBeforeContract(application);
        when(contracts.findCurrentByApplicationIdForUpdate(applicationId)).thenReturn(Optional.of(prepared));
        when(prepared.id()).thenReturn(contractId);
        when(prepared.contractVersion()).thenReturn(2);
        when(acknowledgments.findByLoanContractIdAndContractVersion(contractId, 2))
                .thenReturn(Optional.empty());
    }
}
