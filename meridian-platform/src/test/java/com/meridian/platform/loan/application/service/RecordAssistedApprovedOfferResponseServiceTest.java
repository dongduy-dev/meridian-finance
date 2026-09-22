package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.mapper.ApprovedOfferMapper;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionResult;
import com.meridian.platform.loan.application.port.in.RecordAssistedApprovedOfferResponseUseCase;
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
class RecordAssistedApprovedOfferResponseServiceTest {

    @Mock LoanApplicationRepository applications;
    @Mock ApprovedOfferRepository offers;
    @Mock StaffAssistedOfferResponseRepository responses;
    @Mock LoanAssistedActionEvidencePort evidence;
    @Mock CurrentUserProvider users;
    @Mock LoanApplicationStatusTransitionRecorder transitions;
    @Mock BusinessAuditPublisher audit;
    @Mock ApprovedOfferMapper mapper;
    private RecordAssistedApprovedOfferResponseService service;
    private final UUID requestId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final UUID offerId = UUID.randomUUID();
    private final UUID evidenceVersionId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RecordAssistedApprovedOfferResponseService(
                applications, offers, responses, evidence, users, transitions, audit, mapper,
                Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void acceptsWithExactSignedDecisionEvidenceAndDurableStaffRecord() {
        LoanApplication application = application(OriginationChannel.STAFF_ASSISTED);
        ApprovedOffer pending = mock(ApprovedOffer.class);
        ApprovedOffer accepted = mock(ApprovedOffer.class);
        when(users.currentUser()).thenReturn(loanOfficer());
        when(responses.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application));
        when(offers.findByLoanApplicationIdForUpdate(applicationId)).thenReturn(Optional.of(pending));
        when(pending.id()).thenReturn(offerId);
        when(pending.status()).thenReturn(ApprovedOfferStatus.PENDING);
        when(pending.isExpiredAt(any())).thenReturn(false);
        when(pending.accept(any())).thenReturn(accepted);
        when(accepted.id()).thenReturn(offerId);
        when(accepted.loanApplicationId()).thenReturn(applicationId);
        when(accepted.status()).thenReturn(ApprovedOfferStatus.ACCEPTED);
        when(offers.save(accepted)).thenReturn(accepted);
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(responses.findByApprovedOfferId(offerId)).thenReturn(Optional.empty());
        when(responses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(command(CustomerOfferDecision.ACCEPT));

        verify(evidence).requireCurrentOfferEvidence(applicationId, offerId, "ACCEPT", evidenceVersionId);
        verify(responses).save(argThat(record ->
                record.customerId().equals(application.customerId())
                        && record.recordedByStaffUserId().equals(actorId)
                        && record.action() == CustomerOfferDecision.ACCEPT));
        verify(transitions).record(any(), any(), isNull());
        verify(audit).publish(any());
    }

    @Test
    void declinesAssistedCollateralWithExactSignedDecisionEvidence() {
        LoanApplication application = application(
                OriginationChannel.STAFF_ASSISTED, ProductCode.COLLATERAL_LOAN,
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING);
        ApprovedOffer pending = mock(ApprovedOffer.class);
        ApprovedOffer declined = mock(ApprovedOffer.class);
        preparePending(application, pending);
        when(pending.isExpiredAt(any())).thenReturn(false);
        when(pending.decline(any())).thenReturn(declined);
        when(declined.id()).thenReturn(offerId);
        when(declined.loanApplicationId()).thenReturn(applicationId);
        when(declined.status()).thenReturn(ApprovedOfferStatus.DECLINED);
        when(offers.save(declined)).thenReturn(declined);
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(responses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(command(CustomerOfferDecision.DECLINE));

        verify(evidence).requireCurrentOfferEvidence(applicationId, offerId, "DECLINE", evidenceVersionId);
        verify(responses).save(argThat(record -> record.action() == CustomerOfferDecision.DECLINE
                && record.customerId().equals(application.customerId())
                && record.recordedByStaffUserId().equals(actorId)));
        verify(transitions).record(any(), any(), isNull());
        verify(audit).publish(any());
    }

    @Test
    void rejectsCustomerDigitalChannelBeforeOfferRead() {
        when(users.currentUser()).thenReturn(loanOfficer());
        when(responses.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(applicationId)).thenReturn(Optional.of(
                application(OriginationChannel.CUSTOMER_DIGITAL)));

        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.record(command(CustomerOfferDecision.DECLINE)));

        assertEquals("ASSISTED_ACTION_NOT_ALLOWED", error.getErrorCode());
        verifyNoInteractions(evidence);
        verify(offers, never()).save(any());
    }

    @Test
    void salaryAdvanceCannotBeRepresentedAsStaffAssisted() {
        assertThrows(IllegalArgumentException.class, () -> application(
                OriginationChannel.STAFF_ASSISTED, ProductCode.SALARY_ADVANCE,
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING));
    }

    @Test
    void rejectsCustomerActorStaffWithCustomerIdentityMissingPermissionAndWrongRole() {
        AuthenticatedUser customer = new AuthenticatedUser(actorId, "customer@meridian.test", "CUSTOMER",
                UUID.randomUUID(), Set.of("CUSTOMER"), Set.of("loan:offer:respond:staff"));
        AuthenticatedUser staffWithCustomer = new AuthenticatedUser(actorId, "staff@meridian.test", "STAFF",
                UUID.randomUUID(), Set.of("LOAN_OFFICER"), Set.of("loan:offer:respond:staff"));
        AuthenticatedUser missingPermission = staff(Set.of("LOAN_OFFICER"), Set.of("loan:read"));
        AuthenticatedUser nearMissPermission = staff(Set.of("LOAN_OFFICER"), Set.of("loan:offer:respond:staff.extra"));
        AuthenticatedUser wrongRole = staff(Set.of("ACCOUNTING_OFFICER"), Set.of("loan:offer:respond:staff"));

        for (AuthenticatedUser invalid : new AuthenticatedUser[] {
                customer, staffWithCustomer, missingPermission, nearMissPermission, wrongRole
        }) {
            reset(users, applications, offers, responses, evidence, transitions, audit, mapper);
            when(users.currentUser()).thenReturn(invalid);
            assertThrows(AuthorizationException.class,
                    () -> service.record(command(CustomerOfferDecision.ACCEPT)));
            verifyNoInteractions(applications, offers, evidence, transitions, audit);
        }
    }

    @Test
    void rejectsWrongApplicationStateMissingOfferAndOfferIdentityMismatch() {
        when(users.currentUser()).thenReturn(loanOfficer());
        when(responses.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application(
                OriginationChannel.STAFF_ASSISTED, ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.CONTRACT_PENDING)));
        assertEquals("OFFER_ACTION_CONFLICT", assertThrows(BusinessStateConflictException.class,
                () -> service.record(command(CustomerOfferDecision.ACCEPT))).getErrorCode());

        reset(applications, offers, responses);
        when(responses.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application(
                OriginationChannel.STAFF_ASSISTED, ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING)));
        when(offers.findByLoanApplicationIdForUpdate(applicationId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.record(command(CustomerOfferDecision.ACCEPT)));

        ApprovedOffer otherOffer = mock(ApprovedOffer.class);
        reset(applications, offers, responses);
        when(responses.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application(
                OriginationChannel.STAFF_ASSISTED, ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING)));
        when(offers.findByLoanApplicationIdForUpdate(applicationId)).thenReturn(Optional.of(otherOffer));
        when(otherOffer.id()).thenReturn(UUID.randomUUID());
        assertEquals("OFFER_ACTION_CONFLICT", assertThrows(BusinessStateConflictException.class,
                () -> service.record(command(CustomerOfferDecision.ACCEPT))).getErrorCode());
    }

    @Test
    void expiredOfferPreservesExpiryTransitionWithoutConsumingEvidence() {
        LoanApplication application = application(OriginationChannel.STAFF_ASSISTED);
        ApprovedOffer pending = mock(ApprovedOffer.class);
        ApprovedOffer expired = mock(ApprovedOffer.class);
        preparePending(application, pending);
        when(pending.isExpiredAt(any())).thenReturn(true);
        when(pending.expire(any())).thenReturn(expired);
        when(expired.id()).thenReturn(offerId);
        when(expired.loanApplicationId()).thenReturn(applicationId);
        when(expired.status()).thenReturn(ApprovedOfferStatus.EXPIRED);
        when(offers.save(expired)).thenReturn(expired);
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(command(CustomerOfferDecision.ACCEPT));

        verifyNoInteractions(evidence);
        verify(responses, never()).save(any());
        verify(transitions).record(any(), any(), isNull());
        verify(audit).publish(any());
    }

    @Test
    void evidenceFailureRollsBackBeforeAnyLoanMutation() {
        LoanApplication application = application(OriginationChannel.STAFF_ASSISTED);
        ApprovedOffer pending = mock(ApprovedOffer.class);
        preparePending(application, pending);
        when(pending.isExpiredAt(any())).thenReturn(false);
        doThrow(new BusinessStateConflictException(
                "ASSISTED_ACTION_EVIDENCE_INVALID", "Evidence mismatch."))
                .when(evidence).requireCurrentOfferEvidence(applicationId, offerId, "ACCEPT", evidenceVersionId);

        assertEquals("ASSISTED_ACTION_EVIDENCE_INVALID", assertThrows(BusinessStateConflictException.class,
                () -> service.record(command(CustomerOfferDecision.ACCEPT))).getErrorCode());

        verify(offers, never()).save(any());
        verify(applications, never()).save(any());
        verify(responses, never()).save(any());
        verifyNoInteractions(transitions, audit);
    }

    @Test
    void exactReplayReturnsExistingOfferWithoutRepeatingEffects() {
        StaffAssistedOfferResponse recorded = new StaffAssistedOfferResponse(
                UUID.randomUUID(), requestId, applicationId, UUID.randomUUID(), offerId,
                CustomerOfferDecision.ACCEPT, evidenceVersionId, actorId,
                LocalDateTime.of(2026, 9, 22, 0, 0));
        ApprovedOffer accepted = mock(ApprovedOffer.class);
        when(users.currentUser()).thenReturn(loanOfficer());
        when(responses.findByRequestId(requestId)).thenReturn(Optional.of(recorded));
        when(offers.findByLoanApplicationId(applicationId)).thenReturn(Optional.of(accepted));

        ApprovedOfferActionResult result = service.record(command(CustomerOfferDecision.ACCEPT));

        assertSame(null, result.offer());
        verifyNoInteractions(applications, evidence, transitions, audit);
        verify(offers, never()).save(any());
        verify(responses, never()).save(any());
    }

    @Test
    void replayRequestIdentityWithDifferentSemanticsFailsClosed() {
        StaffAssistedOfferResponse recorded = new StaffAssistedOfferResponse(
                UUID.randomUUID(), requestId, applicationId, UUID.randomUUID(), offerId,
                CustomerOfferDecision.DECLINE, evidenceVersionId, actorId,
                LocalDateTime.of(2026, 9, 22, 0, 0));
        when(users.currentUser()).thenReturn(loanOfficer());
        when(responses.findByRequestId(requestId)).thenReturn(Optional.of(recorded));

        assertEquals("IDEMPOTENCY_KEY_REUSED", assertThrows(BusinessStateConflictException.class,
                () -> service.record(command(CustomerOfferDecision.ACCEPT))).getErrorCode());
        verifyNoInteractions(applications, offers, evidence, transitions, audit);
    }

    private RecordAssistedApprovedOfferResponseUseCase.Command command(CustomerOfferDecision decision) {
        return new RecordAssistedApprovedOfferResponseUseCase.Command(
                requestId, applicationId, offerId, decision, evidenceVersionId);
    }

    private LoanApplication application(OriginationChannel channel) {
        return application(channel, ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING);
    }

    private LoanApplication application(
            OriginationChannel channel, ProductCode productCode, LoanApplicationStatus status
    ) {
        return new LoanApplication(applicationId, UUID.randomUUID(), UUID.randomUUID(), "UCL-1",
                productCode, productCode == ProductCode.COLLATERAL_LOAN ? ProductType.SECURED : ProductType.UNSECURED,
                channel, status, BigDecimal.valueOf(5_000_000), 6,
                LocalDateTime.of(2026, 9, 20, 0, 0));
    }

    private AuthenticatedUser loanOfficer() {
        return staff(Set.of("LOAN_OFFICER"), Set.of("loan:offer:respond:staff"));
    }

    private AuthenticatedUser staff(Set<String> roles, Set<String> permissions) {
        return new AuthenticatedUser(actorId, "officer@meridian.test", "STAFF", null, roles, permissions);
    }

    private void preparePending(LoanApplication application, ApprovedOffer pending) {
        when(users.currentUser()).thenReturn(loanOfficer());
        when(responses.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(applications.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application));
        when(offers.findByLoanApplicationIdForUpdate(applicationId)).thenReturn(Optional.of(pending));
        when(pending.id()).thenReturn(offerId);
        when(pending.status()).thenReturn(ApprovedOfferStatus.PENDING);
        when(responses.findByApprovedOfferId(offerId)).thenReturn(Optional.empty());
    }
}
