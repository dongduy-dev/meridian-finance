package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApprovedOfferActionOutcome;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionResult;
import com.meridian.platform.loan.application.mapper.ApprovedOfferMapper;
import com.meridian.platform.loan.application.port.in.RecordAssistedApprovedOfferResponseUseCase;
import com.meridian.platform.loan.application.port.out.*;
import com.meridian.platform.loan.domain.model.*;
import com.meridian.platform.shared.application.audit.*;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.*;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class RecordAssistedApprovedOfferResponseService implements RecordAssistedApprovedOfferResponseUseCase {

    private final LoanApplicationRepository applications;
    private final ApprovedOfferRepository offers;
    private final StaffAssistedOfferResponseRepository responses;
    private final LoanAssistedActionEvidencePort evidence;
    private final CurrentUserProvider currentUsers;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher auditPublisher;
    private final ApprovedOfferMapper mapper;
    private final Clock clock;

    public RecordAssistedApprovedOfferResponseService(
            LoanApplicationRepository applications,
            ApprovedOfferRepository offers,
            StaffAssistedOfferResponseRepository responses,
            LoanAssistedActionEvidencePort evidence,
            CurrentUserProvider currentUsers,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher auditPublisher,
            ApprovedOfferMapper mapper,
            Clock clock
    ) {
        this.applications = applications;
        this.offers = offers;
        this.responses = responses;
        this.evidence = evidence;
        this.currentUsers = currentUsers;
        this.transitionRecorder = transitionRecorder;
        this.auditPublisher = auditPublisher;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ApprovedOfferActionResult record(Command command) {
        requireCommand(command);
        AuthenticatedUser actor = requireActor();
        responses.acquireRequestLock(command.requestId());
        StaffAssistedOfferResponse replay = responses.findByRequestId(command.requestId()).orElse(null);
        if (replay != null) return replayResult(replay, command, actor.userId());

        applications.acquireWorkflowLock(command.loanApplicationId());
        LoanApplication application = applications.findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND", "Loan application was not found."));
        requireEligible(application);
        ApprovedOffer offer = offers.findByLoanApplicationIdForUpdate(application.id())
                .orElseThrow(() -> new EntityNotFoundException(
                        "APPROVED_OFFER_NOT_FOUND", "Approved offer was not found."));
        if (!offer.id().equals(command.expectedApprovedOfferId())) {
            throw conflict("OFFER_ACTION_CONFLICT", "Approved offer identity is stale.");
        }
        replay = responses.findByRequestId(command.requestId()).orElse(null);
        if (replay != null) return replayResult(replay, command, actor.userId());
        if (responses.findByApprovedOfferId(offer.id()).isPresent()) {
            throw conflict("OFFER_ACTION_CONFLICT", "The approved offer already has a recorded response.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (offer.status() == ApprovedOfferStatus.EXPIRED) {
            return new ApprovedOfferActionResult(ApprovedOfferActionOutcome.EXPIRED, mapper.toDto(offer, now));
        }
        if (offer.status() != ApprovedOfferStatus.PENDING) {
            throw conflict("OFFER_ACTION_CONFLICT", "Approved offer action conflicts with the current offer state.");
        }
        if (offer.isExpiredAt(now)) {
            ApprovedOffer expired = offers.save(offer.expire(now));
            LoanApplicationTransitionResult transition = application.expireApprovedOffer();
            applications.save(transition.loanApplication());
            BusinessOperationContext operation = BusinessOperationContext.system(command.requestId(), now);
            transitionRecorder.record(operation, transition.facts(), null);
            audit(operation, BusinessAuditAction.OFFER_EXPIRED, expired, application.customerId(), null);
            return new ApprovedOfferActionResult(ApprovedOfferActionOutcome.EXPIRED, mapper.toDto(expired, now));
        }

        evidence.requireCurrentOfferEvidence(application.id(), offer.id(), command.action().name(),
                command.evidenceDocumentVersionId());
        ApprovedOffer resolved = command.action() == CustomerOfferDecision.ACCEPT
                ? offer.accept(now) : offer.decline(now);
        LoanApplicationTransitionResult transition = command.action() == CustomerOfferDecision.ACCEPT
                ? application.acceptApprovedOffer() : application.declineApprovedOffer();
        ApprovedOffer savedOffer = offers.save(resolved);
        applications.save(transition.loanApplication());
        StaffAssistedOfferResponse response = responses.save(new StaffAssistedOfferResponse(
                UUID.randomUUID(), command.requestId(), application.id(), application.customerId(), offer.id(),
                command.action(), command.evidenceDocumentVersionId(), actor.userId(), now));
        BusinessOperationContext operation = BusinessOperationContext.user(command.requestId(), actor.userId(), now);
        transitionRecorder.record(operation, transition.facts(), null);
        audit(operation, command.action() == CustomerOfferDecision.ACCEPT
                        ? BusinessAuditAction.APPROVED_OFFER_ACCEPTED
                        : BusinessAuditAction.APPROVED_OFFER_DECLINED,
                savedOffer, response.customerId(), response.evidenceDocumentVersionId());
        return new ApprovedOfferActionResult(ApprovedOfferActionOutcome.SUCCESS, mapper.toDto(savedOffer, now));
    }

    private ApprovedOfferActionResult replayResult(StaffAssistedOfferResponse replay, Command command, UUID actorId) {
        if (!replay.loanApplicationId().equals(command.loanApplicationId())
                || !replay.approvedOfferId().equals(command.expectedApprovedOfferId())
                || replay.action() != command.action()
                || !replay.evidenceDocumentVersionId().equals(command.evidenceDocumentVersionId())
                || !replay.recordedByStaffUserId().equals(actorId)) {
            throw conflict("IDEMPOTENCY_KEY_REUSED", "Command request ID was already used for different content.");
        }
        ApprovedOffer offer = offers.findByLoanApplicationId(command.loanApplicationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "APPROVED_OFFER_NOT_FOUND", "Approved offer was not found."));
        return new ApprovedOfferActionResult(ApprovedOfferActionOutcome.SUCCESS,
                mapper.toDto(offer, LocalDateTime.now(clock)));
    }

    private AuthenticatedUser requireActor() {
        AuthenticatedUser actor = currentUsers.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:offer:respond:staff")) {
            throw new AuthorizationException("ASSISTED_ACTION_ACCESS_DENIED",
                    "Staff-assisted offer response access is denied.");
        }
        if (!actor.roles().contains("LOAN_OFFICER")) {
            throw new AuthorizationException("ASSISTED_ACTION_ROLE_REQUIRED",
                    "Loan Officer authority is required to record an assisted offer response.");
        }
        return actor;
    }

    private static void requireEligible(LoanApplication application) {
        if (application.originationChannel() != OriginationChannel.STAFF_ASSISTED
                || (application.productCode() != ProductCode.UNSECURED_CONSUMER_LOAN
                && application.productCode() != ProductCode.COLLATERAL_LOAN)) {
            throw conflict("ASSISTED_ACTION_NOT_ALLOWED",
                    "Only Staff-assisted UCL or Collateral Loan permits this action.");
        }
        if (application.status() != LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING) {
            throw conflict("OFFER_ACTION_CONFLICT", "The application is not awaiting Customer offer response.");
        }
    }

    private void audit(
            BusinessOperationContext operation,
            BusinessAuditAction action,
            ApprovedOffer offer,
            UUID customerId,
            UUID documentVersionId
    ) {
        BusinessAuditPayload.Builder payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, offer.loanApplicationId())
                .put(BusinessAuditPayloadKey.CUSTOMER_ID, customerId)
                .put(BusinessAuditPayloadKey.OFFER_STATUS, offer.status());
        if (documentVersionId != null) {
            payload.put(BusinessAuditPayloadKey.DOCUMENT_VERSION_ID, documentVersionId);
        }
        auditPublisher.publish(BusinessAuditEvent.single(operation, new BusinessAuditEntry(
                action, BusinessAuditEntityType.APPROVED_OFFER, offer.id(), payload.build())));
    }

    private static void requireCommand(Command command) {
        Objects.requireNonNull(command);
        Objects.requireNonNull(command.requestId());
        Objects.requireNonNull(command.loanApplicationId());
        Objects.requireNonNull(command.expectedApprovedOfferId());
        Objects.requireNonNull(command.action());
        Objects.requireNonNull(command.evidenceDocumentVersionId());
    }

    private static BusinessStateConflictException conflict(String code, String message) {
        return new BusinessStateConflictException(code, message);
    }
}
