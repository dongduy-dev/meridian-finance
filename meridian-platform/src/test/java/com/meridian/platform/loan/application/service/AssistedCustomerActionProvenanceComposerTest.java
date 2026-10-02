package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.*;
import com.meridian.platform.loan.domain.model.*;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.model.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistedCustomerActionProvenanceComposerTest {
    private final UUID appId = UUID.randomUUID(), customerId = UUID.randomUUID(), offerId = UUID.randomUUID();
    private final UUID staffId = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private final UUID versionId = UUID.randomUUID(), requestId = UUID.randomUUID(), correctionId = UUID.randomUUID();
    private final LocalDateTime at = LocalDateTime.of(2026, 10, 1, 8, 0);
    private final ApprovedOfferRepository offers = mock(ApprovedOfferRepository.class);
    private final StaffAssistedOfferResponseRepository responses = mock(StaffAssistedOfferResponseRepository.class);
    private final LoanApplicationCancellationRepository cancellations = mock(LoanApplicationCancellationRepository.class);
    private final LoanCorrectionRepository corrections = mock(LoanCorrectionRepository.class);
    private final LoanAssistedActionEvidencePort evidence = mock(LoanAssistedActionEvidencePort.class);
    private final LoanApplicationStatusTransitionRepository transitions = mock(LoanApplicationStatusTransitionRepository.class);
    private final StaffActorDirectoryPort actors = mock(StaffActorDirectoryPort.class);
    private final LoanApplication application = mock(LoanApplication.class);
    private final ApprovedOffer offer = mock(ApprovedOffer.class);
    private final LoanCorrectionRequest correction = mock(LoanCorrectionRequest.class);
    private final AssistedCustomerActionProvenanceComposer composer = new AssistedCustomerActionProvenanceComposer(
            offers, responses, cancellations, corrections, evidence, transitions, actors);

    @BeforeEach void setup() {
        when(application.id()).thenReturn(appId);
        when(application.customerId()).thenReturn(customerId);
        when(application.originationChannel()).thenReturn(OriginationChannel.STAFF_ASSISTED);
        when(application.productCode()).thenReturn(ProductCode.UNSECURED_CONSUMER_LOAN);
        when(offer.id()).thenReturn(offerId);
        when(offer.loanApplicationId()).thenReturn(appId);
        when(actors.findByUserIds(Set.of(staffId))).thenReturn(Map.of(
                staffId, new StaffActorSummary(staffId, "Deni Loan Officer", "deni@meridian.local")));
    }

    @ParameterizedTest @EnumSource(CustomerOfferDecision.class)
    void completedOfferPreservesCustomerDecisionStaffRecorderTimeAndExactEvidence(CustomerOfferDecision action) {
        completedOffer(action);
        var result = composer.offer(application, offer);
        assertEquals(action.name(), result.action());
        assertEquals(staffId, result.recordedBy().userId());
        assertEquals(at, result.recordedAt());
        assertEquals(versionId, result.evidence().documentVersionId());
        assertEquals(offerId, result.evidence().targetId());
        verify(actors).findByUserIds(Set.of(staffId));
        verify(responses, never()).save(any());
        verify(offers, never()).save(any());
        verify(transitions, never()).save(any());
        assertEquals(action == CustomerOfferDecision.ACCEPT ? "CUSTOMER_OFFER_ACCEPTANCE_RECORDED"
                : "CUSTOMER_OFFER_DECLINE_RECORDED", composer.historyActions(application, history()).values().iterator().next());
    }

    @Test void pendingOfferHasNoCompletedProvenance() {
        when(offer.status()).thenReturn(ApprovedOfferStatus.PENDING);
        assertNull(composer.offer(application, offer));
        verifyNoInteractions(evidence, transitions);
        verify(actors, never()).findByUserIds(anySet());
    }

    @Test void missingCompletedRecordFailsClosed() {
        when(offer.status()).thenReturn(ApprovedOfferStatus.ACCEPTED);
        conflict(() -> composer.offer(application, offer));
    }

    @Test void contradictoryOfferSubjectsTargetsActionsTimesAndStatusesFailClosed() {
        for (int kind = 0; kind < 6; kind++) {
            completedOffer(CustomerOfferDecision.ACCEPT);
            var response = new StaffAssistedOfferResponse(UUID.randomUUID(), requestId,
                    kind == 0 ? UUID.randomUUID() : appId, kind == 1 ? UUID.randomUUID() : customerId,
                    kind == 2 ? UUID.randomUUID() : offerId,
                    kind == 3 ? CustomerOfferDecision.DECLINE : CustomerOfferDecision.ACCEPT,
                    versionId, staffId, kind == 4 ? at.plusSeconds(1) : at);
            when(responses.findByApprovedOfferId(offerId)).thenReturn(Optional.of(response));
            if (kind == 5) when(application.status()).thenReturn(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING);
            conflict(() -> composer.offer(application, offer));
        }
    }

    @Test void missingMismatchedEvidenceOrUnsafeRecorderFailsClosed() {
        completedOffer(CustomerOfferDecision.ACCEPT);
        when(evidence.findOfferEvidence(appId, offerId)).thenReturn(Optional.empty());
        conflict(() -> composer.offer(application, offer));
        when(evidence.findOfferEvidence(appId, offerId)).thenReturn(Optional.of(document(UUID.randomUUID(), "ACCEPT", false)));
        conflict(() -> composer.offer(application, offer));
        when(evidence.findOfferEvidence(appId, offerId)).thenReturn(Optional.of(document(versionId, "DECLINE", false)));
        conflict(() -> composer.offer(application, offer));
        when(evidence.findOfferEvidence(appId, offerId)).thenReturn(Optional.of(document(versionId, "ACCEPT", false)));
        when(actors.findByUserIds(Set.of(staffId))).thenReturn(Map.of());
        conflict(() -> composer.offer(application, offer));
    }

    @Test void offerTransitionMustMatchOperationActorStatusTimeAndBeUnique() {
        completedOffer(CustomerOfferDecision.ACCEPT);
        for (int kind = 0; kind < 5; kind++) {
            var row = new LoanApplicationStatusTransition(UUID.randomUUID(), appId,
                    kind == 0 ? UUID.randomUUID() : requestId, 1,
                    kind == 1 ? LoanApplicationStatus.APPROVED : LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                    LoanApplicationStatus.CONTRACT_PENDING, LoanApplicationTransitionAction.ACCEPT_APPROVED_OFFER,
                    null, ActorType.USER, kind == 2 ? UUID.randomUUID() : staffId,
                    kind == 3 ? at.plusSeconds(1) : at);
            when(transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(appId))
                    .thenReturn(kind == 4 ? List.of(row, row) : List.of(row));
            conflict(() -> composer.offer(application, offer));
        }
    }

    @Test void completedCancellationPreservesCustomerRequestAndExactStaffEvidence() {
        completedCancellation();
        var result = composer.cancellation(application, correction);
        assertEquals("CUSTOMER_REQUESTED_CANCELLATION", result.action());
        assertEquals(staffId, result.recordedBy().userId());
        assertEquals(at, result.recordedAt());
        assertEquals(versionId, result.evidence().documentVersionId());
        assertEquals(correctionId, result.evidence().targetId());
        assertEquals("CUSTOMER_REQUESTED_CANCELLATION_RECORDED",
                composer.historyActions(application, history()).values().iterator().next());
        verify(cancellations, never()).saveIfAbsent(any());
        verify(transitions, never()).save(any());
    }

    @Test void digitalCancellationNeverBecomesStaffAssisted() {
        when(application.originationChannel()).thenReturn(OriginationChannel.CUSTOMER_DIGITAL);
        when(cancellations.findByLoanApplicationId(appId)).thenReturn(Optional.of(new LoanApplicationCancellation(
                UUID.randomUUID(), appId, correctionId, null, requestId, UUID.randomUUID(), null, at)));
        assertNull(composer.cancellation(application, correction));
        verifyNoInteractions(evidence, transitions);
    }

    @Test void cancelledAssistedApplicationRequiresExactCancelledCorrectionAndEvidence() {
        completedCancellation();
        when(cancellations.findByLoanApplicationId(appId)).thenReturn(Optional.empty());
        conflict(() -> composer.cancellation(application, correction));
        completedCancellation();
        when(correction.cancelledAt()).thenReturn(at.plusSeconds(1));
        conflict(() -> composer.cancellation(application, correction));
        completedCancellation();
        when(evidence.findCancellationEvidence(appId, correctionId)).thenReturn(Optional.empty());
        conflict(() -> composer.cancellation(application, correction));
        when(evidence.findCancellationEvidence(appId, correctionId))
                .thenReturn(Optional.of(document(UUID.randomUUID(), null, true)));
        conflict(() -> composer.cancellation(application, correction));
    }

    private void completedOffer(CustomerOfferDecision action) {
        boolean accept = action == CustomerOfferDecision.ACCEPT;
        when(application.status()).thenReturn(accept ? LoanApplicationStatus.CONTRACT_PENDING : LoanApplicationStatus.CUSTOMER_DECLINED);
        when(offer.status()).thenReturn(accept ? ApprovedOfferStatus.ACCEPTED : ApprovedOfferStatus.DECLINED);
        when(offer.acceptedAt()).thenReturn(accept ? at : null);
        when(offer.declinedAt()).thenReturn(accept ? null : at);
        var response = new StaffAssistedOfferResponse(UUID.randomUUID(), requestId, appId, customerId,
                offerId, action, versionId, staffId, at);
        when(offers.findByLoanApplicationId(appId)).thenReturn(Optional.of(offer));
        when(responses.findByApprovedOfferId(offerId)).thenReturn(Optional.of(response));
        when(evidence.findOfferEvidence(appId, offerId)).thenReturn(Optional.of(document(versionId, action.name(), false)));
        when(transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(appId)).thenReturn(List.of(
                new LoanApplicationStatusTransition(UUID.randomUUID(), appId, requestId, 1,
                        LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                        accept ? LoanApplicationStatus.CONTRACT_PENDING : LoanApplicationStatus.CUSTOMER_DECLINED,
                        accept ? LoanApplicationTransitionAction.ACCEPT_APPROVED_OFFER : LoanApplicationTransitionAction.DECLINE_APPROVED_OFFER,
                        null, ActorType.USER, staffId, at)));
    }

    private void completedCancellation() {
        when(application.status()).thenReturn(LoanApplicationStatus.CANCELLED);
        var cancellation = new LoanApplicationCancellation(UUID.randomUUID(), appId, correctionId,
                null, requestId, staffId, versionId, at);
        when(cancellations.findByLoanApplicationId(appId)).thenReturn(Optional.of(cancellation));
        when(correction.id()).thenReturn(correctionId);
        when(correction.loanApplicationId()).thenReturn(appId);
        when(correction.status()).thenReturn(LoanCorrectionRequestStatus.CANCELLED);
        when(correction.cancelledAt()).thenReturn(at);
        when(corrections.findRequestById(correctionId)).thenReturn(Optional.of(correction));
        when(evidence.findCancellationEvidence(appId, correctionId)).thenReturn(Optional.of(document(versionId, null, true)));
        when(transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(appId)).thenReturn(List.of(
                new LoanApplicationStatusTransition(UUID.randomUUID(), appId, cancellation.id(), 1,
                        LoanApplicationStatus.RETURNED_FOR_REVISION, LoanApplicationStatus.CANCELLED,
                        LoanApplicationTransitionAction.CANCEL_APPLICATION, null, ActorType.USER, staffId, at)));
    }

    private LoanAssistedActionEvidencePort.EvidenceSnapshot document(UUID version, String decision, boolean cancel) {
        return new LoanAssistedActionEvidencePort.EvidenceSnapshot(UUID.randomUUID(), version,
                cancel ? "CUSTOMER_CANCELLATION_REQUEST" : "CUSTOMER_OFFER_RESPONSE",
                cancel ? null : offerId, decision, null, null, cancel ? correctionId : null,
                2, "application/pdf", 2048, at.minusMinutes(1));
    }

    private List<LoanApplicationStatusTransition> history() {
        return transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(appId);
    }

    private static void conflict(org.junit.jupiter.api.function.Executable action) {
        assertEquals("SYSTEM_STATE_CONFLICT", assertThrows(BusinessStateConflictException.class, action).getErrorCode());
    }
}
