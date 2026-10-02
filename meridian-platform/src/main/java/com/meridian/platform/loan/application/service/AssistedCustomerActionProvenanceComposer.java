package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.RecordedCustomerActionDto;
import com.meridian.platform.loan.application.dto.StaffLoanApplicationCaseDto;
import com.meridian.platform.loan.application.port.out.*;
import com.meridian.platform.loan.domain.model.*;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.model.ActorType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Composes existing terminal Customer decisions; never records or repairs evidence. */
@Component
public class AssistedCustomerActionProvenanceComposer {
    private final ApprovedOfferRepository offers;
    private final StaffAssistedOfferResponseRepository responses;
    private final LoanApplicationCancellationRepository cancellations;
    private final LoanCorrectionRepository corrections;
    private final LoanAssistedActionEvidencePort evidence;
    private final LoanApplicationStatusTransitionRepository transitions;
    private final StaffActorDirectoryPort staffActors;

    public AssistedCustomerActionProvenanceComposer(
            ApprovedOfferRepository offers, StaffAssistedOfferResponseRepository responses,
            LoanApplicationCancellationRepository cancellations, LoanCorrectionRepository corrections,
            LoanAssistedActionEvidencePort evidence, LoanApplicationStatusTransitionRepository transitions,
            StaffActorDirectoryPort staffActors
    ) {
        this.offers = offers;
        this.responses = responses;
        this.cancellations = cancellations;
        this.corrections = corrections;
        this.evidence = evidence;
        this.transitions = transitions;
        this.staffActors = staffActors;
    }

    public RecordedCustomerActionDto offer(LoanApplication application, ApprovedOffer offer) {
        var response = responses.findByApprovedOfferId(offer.id()).orElse(null);
        boolean completed = offer.status() == ApprovedOfferStatus.ACCEPTED
                || offer.status() == ApprovedOfferStatus.DECLINED;
        if (!application.id().equals(offer.loanApplicationId()) || completed != (response != null)) throw conflict();
        if (!completed) return null;
        validateOffer(application, offer, response, history(application));
        var document = evidence.findOfferEvidence(application.id(), offer.id()).orElseThrow(
                AssistedCustomerActionProvenanceComposer::conflict);
        if (!"CUSTOMER_OFFER_RESPONSE".equals(document.evidenceType())
                || !offer.id().equals(document.approvedOfferId())
                || !response.action().name().equals(document.declaredOfferDecision())
                || !response.evidenceDocumentVersionId().equals(document.documentVersionId())
                || document.uploadedAt().isAfter(response.recordedAt())) throw conflict();
        return new RecordedCustomerActionDto(response.action().name(), actor(response.recordedByStaffUserId()),
                response.recordedAt(), QueryAssistedApprovedOfferResponseService.toDto(document));
    }

    public RecordedCustomerActionDto cancellation(LoanApplication application, LoanCorrectionRequest request) {
        var cancellation = cancellations.findByLoanApplicationId(application.id()).orElse(null);
        if (application.originationChannel() != OriginationChannel.STAFF_ASSISTED) {
            if (cancellation != null && cancellation.assistedEvidenceDocumentVersionId() != null) throw conflict();
            return null;
        }
        if (application.status() != LoanApplicationStatus.CANCELLED) {
            if (cancellation != null) throw conflict();
            return null;
        }
        validateCancellation(application, request, cancellation, history(application));
        var document = evidence.findCancellationEvidence(application.id(), request.id()).orElseThrow(
                AssistedCustomerActionProvenanceComposer::conflict);
        if (!"CUSTOMER_CANCELLATION_REQUEST".equals(document.evidenceType())
                || !request.id().equals(document.correctionRequestId())
                || !cancellation.assistedEvidenceDocumentVersionId().equals(document.documentVersionId())
                || document.uploadedAt().isAfter(cancellation.cancelledAt())) throw conflict();
        return new RecordedCustomerActionDto("CUSTOMER_REQUESTED_CANCELLATION",
                actor(cancellation.cancelledByUserId()), cancellation.cancelledAt(),
                QueryAssistedApprovedOfferResponseService.toDto(document));
    }

    /** Only high-level wording crosses into the general lifecycle projection. */
    public Map<UUID, String> historyActions(LoanApplication application, List<LoanApplicationStatusTransition> history) {
        if (application.originationChannel() != OriginationChannel.STAFF_ASSISTED) return Map.of();
        Map<UUID, String> result = new java.util.LinkedHashMap<>();
        if (history.stream().anyMatch(row -> row.action() == LoanApplicationTransitionAction.ACCEPT_APPROVED_OFFER
                || row.action() == LoanApplicationTransitionAction.DECLINE_APPROVED_OFFER)) {
            var offer = offers.findByLoanApplicationId(application.id()).orElseThrow(
                    AssistedCustomerActionProvenanceComposer::conflict);
            var response = responses.findByApprovedOfferId(offer.id()).orElseThrow(
                    AssistedCustomerActionProvenanceComposer::conflict);
            var row = validateOffer(application, offer, response, history);
            result.put(row.id(), response.action() == CustomerOfferDecision.ACCEPT
                    ? "CUSTOMER_OFFER_ACCEPTANCE_RECORDED" : "CUSTOMER_OFFER_DECLINE_RECORDED");
        }
        if (history.stream().anyMatch(row -> row.action() == LoanApplicationTransitionAction.CANCEL_APPLICATION)) {
            var cancellation = cancellations.findByLoanApplicationId(application.id()).orElseThrow(
                    AssistedCustomerActionProvenanceComposer::conflict);
            var request = corrections.findRequestById(cancellation.correctionRequestId()).orElseThrow(
                    AssistedCustomerActionProvenanceComposer::conflict);
            var row = validateCancellation(application, request, cancellation, history);
            result.put(row.id(), "CUSTOMER_REQUESTED_CANCELLATION_RECORDED");
        }
        return result;
    }

    private List<LoanApplicationStatusTransition> history(LoanApplication application) {
        return transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(application.id());
    }

    private static LoanApplicationStatusTransition validateOffer(
            LoanApplication application, ApprovedOffer offer, StaffAssistedOfferResponse response,
            List<LoanApplicationStatusTransition> history
    ) {
        boolean accepted = response.action() == CustomerOfferDecision.ACCEPT;
        if (application.originationChannel() != OriginationChannel.STAFF_ASSISTED
                || (application.productCode() != ProductCode.UNSECURED_CONSUMER_LOAN
                    && application.productCode() != ProductCode.COLLATERAL_LOAN)
                || (accepted ? !Set.of(LoanApplicationStatus.CONTRACT_PENDING, LoanApplicationStatus.DISBURSEMENT_PENDING,
                        LoanApplicationStatus.DISBURSED).contains(application.status())
                    : application.status() != LoanApplicationStatus.CUSTOMER_DECLINED)
                || !application.id().equals(offer.loanApplicationId())
                || !application.id().equals(response.loanApplicationId())
                || !application.customerId().equals(response.customerId())
                || !offer.id().equals(response.approvedOfferId())
                || offer.status() != (accepted ? ApprovedOfferStatus.ACCEPTED : ApprovedOfferStatus.DECLINED)
                || !response.recordedAt().equals(accepted ? offer.acceptedAt() : offer.declinedAt())) throw conflict();
        return transition(history, application.id(), accepted
                        ? LoanApplicationTransitionAction.ACCEPT_APPROVED_OFFER
                        : LoanApplicationTransitionAction.DECLINE_APPROVED_OFFER,
                LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                accepted ? LoanApplicationStatus.CONTRACT_PENDING : LoanApplicationStatus.CUSTOMER_DECLINED,
                response.requestId(), response.recordedByStaffUserId(), response.recordedAt());
    }

    private static LoanApplicationStatusTransition validateCancellation(
            LoanApplication application, LoanCorrectionRequest request, LoanApplicationCancellation cancellation,
            List<LoanApplicationStatusTransition> history
    ) {
        if (application.productCode() != ProductCode.UNSECURED_CONSUMER_LOAN
                || application.originationChannel() != OriginationChannel.STAFF_ASSISTED
                || application.status() != LoanApplicationStatus.CANCELLED || request == null || cancellation == null
                || !application.id().equals(cancellation.loanApplicationId())
                || !application.id().equals(request.loanApplicationId())
                || !request.id().equals(cancellation.correctionRequestId())
                || request.status() != LoanCorrectionRequestStatus.CANCELLED
                || !cancellation.cancelledAt().equals(request.cancelledAt())
                || cancellation.assistedEvidenceDocumentVersionId() == null
                || cancellation.reservationReleaseMovementId() != null) throw conflict();
        return transition(history, application.id(), LoanApplicationTransitionAction.CANCEL_APPLICATION,
                LoanApplicationStatus.RETURNED_FOR_REVISION, LoanApplicationStatus.CANCELLED,
                cancellation.id(), cancellation.cancelledByUserId(), cancellation.cancelledAt());
    }

    private static LoanApplicationStatusTransition transition(
            List<LoanApplicationStatusTransition> history, UUID applicationId, LoanApplicationTransitionAction action,
            LoanApplicationStatus from, LoanApplicationStatus to, UUID operationId, UUID actorId, LocalDateTime at
    ) {
        var matching = history.stream().filter(row -> row.action() == action).toList();
        if (matching.size() != 1) throw conflict();
        var row = matching.getFirst();
        if (!applicationId.equals(row.loanApplicationId()) || row.fromStatus() != from || row.toStatus() != to
                || row.actorType() != ActorType.USER || !Objects.equals(actorId, row.actorUserId())
                || !operationId.equals(row.operationId()) || !at.equals(row.occurredAt())) throw conflict();
        return row;
    }

    private StaffLoanApplicationCaseDto.StaffActorDto actor(UUID id) {
        var values = staffActors.findByUserIds(Set.of(id));
        var actor = values == null ? null : values.get(id);
        if (actor == null || !id.equals(actor.userId()) || actor.displayName() == null
                || actor.displayName().isBlank() || actor.email() == null || actor.email().isBlank()) throw conflict();
        return new StaffLoanApplicationCaseDto.StaffActorDto(id, actor.displayName(), actor.email());
    }

    private static BusinessStateConflictException conflict() {
        return new BusinessStateConflictException("SYSTEM_STATE_CONFLICT", "Assisted Customer action evidence is inconsistent.");
    }
}
