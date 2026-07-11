package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApprovedOfferActionOutcome;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionResult;
import com.meridian.platform.loan.application.mapper.ApprovedOfferMapper;
import com.meridian.platform.loan.application.port.in.RespondToApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.shared.application.audit.AuditAction;
import com.meridian.platform.shared.application.audit.AuditEntityType;
import com.meridian.platform.shared.application.audit.AuditEventPublisher;
import com.meridian.platform.shared.application.audit.AuditRecordRequestedEvent;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActionActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class RespondToApprovedOfferService implements RespondToApprovedOfferUseCase {
    private final LoanApplicationRepository loanApplicationRepository;
    private final ApprovedOfferRepository approvedOfferRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService;
    private final ApprovedOfferMapper approvedOfferMapper;
    private final Clock clock;
    private final LoanApplicationLifecycleHistoryRecorder historyRecorder;
    private final AuditEventPublisher auditEventPublisher;

    public RespondToApprovedOfferService(
            LoanApplicationRepository loanApplicationRepository,
            ApprovedOfferRepository approvedOfferRepository,
            CurrentUserProvider currentUserProvider,
            SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService,
            ApprovedOfferMapper approvedOfferMapper,
            Clock clock,
            LoanApplicationLifecycleHistoryRecorder historyRecorder,
            AuditEventPublisher auditEventPublisher
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.approvedOfferRepository = approvedOfferRepository;
        this.currentUserProvider = currentUserProvider;
        this.salaryAdvanceReservationReleaseService = salaryAdvanceReservationReleaseService;
        this.approvedOfferMapper = approvedOfferMapper;
        this.clock = clock;
        this.historyRecorder = historyRecorder;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Override
    @Transactional
    public ApprovedOfferActionResult acceptOffer(UUID loanApplicationId) {
        return respond(loanApplicationId, OfferResponseAction.ACCEPT);
    }

    @Override
    @Transactional
    public ApprovedOfferActionResult declineOffer(UUID loanApplicationId) {
        return respond(loanApplicationId, OfferResponseAction.DECLINE);
    }

    private ApprovedOfferActionResult respond(UUID loanApplicationId, OfferResponseAction action) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        UUID customerId = currentUser.requireCustomerId();
        LocalDateTime now = LocalDateTime.now(clock);
        LoanApplication application = loanApplicationRepository.findByIdForUpdate(loanApplicationId).orElseThrow(() ->
                new EntityNotFoundException("LOAN_APPLICATION_NOT_FOUND", "Loan application was not found."));
        assertOwnApplication(application, customerId);
        ApprovedOffer offer = approvedOfferRepository.findByLoanApplicationIdForUpdate(loanApplicationId).orElseThrow(() ->
                new EntityNotFoundException("APPROVED_OFFER_NOT_FOUND", "Approved offer was not found for the loan application."));
        if (isIdempotentCurrentResult(offer, action)) {
            return success(offer, now);
        }
        if (offer.status() != ApprovedOfferStatus.PENDING) {
            throw offerActionConflict();
        }

        UUID operationId = UUID.randomUUID();
        if (offer.isExpiredAt(now)) {
            ActionActor actor = ActionActor.system();
            LoanApplicationTransitionResult transition = application.expireApprovedOfferWithTransition();
            ApprovedOffer savedOffer = approvedOfferRepository.save(offer.expire(now));
            loanApplicationRepository.save(transition.loanApplication());
            historyRecorder.record(operationId, actor, null, now, transition);
            auditEventPublisher.publish(new AuditRecordRequestedEvent(operationId, (short) 1, actor,
                    AuditEntityType.APPROVED_OFFER, savedOffer.id(), AuditAction.OFFER_EXPIRED, List.of(), now));
            salaryAdvanceReservationReleaseService.releaseReservationOnce(application, now, operationId, actor, (short) 2);
            return new ApprovedOfferActionResult(ApprovedOfferActionOutcome.EXPIRED, approvedOfferMapper.toDto(savedOffer, now));
        }

        ActionActor actor = ActionActor.user(currentUser.userId());
        if (action == OfferResponseAction.ACCEPT) {
            LoanApplicationTransitionResult transition = application.acceptApprovedOfferWithTransition();
            ApprovedOffer savedOffer = approvedOfferRepository.save(offer.accept(now));
            loanApplicationRepository.save(transition.loanApplication());
            historyRecorder.record(operationId, actor, null, now, transition);
            auditEventPublisher.publish(new AuditRecordRequestedEvent(operationId, (short) 1, actor,
                    AuditEntityType.APPROVED_OFFER, savedOffer.id(), AuditAction.OFFER_ACCEPTED, List.of(), now));
            return success(savedOffer, now);
        }
        LoanApplicationTransitionResult transition = application.declineApprovedOfferWithTransition();
        ApprovedOffer savedOffer = approvedOfferRepository.save(offer.decline(now));
        loanApplicationRepository.save(transition.loanApplication());
        historyRecorder.record(operationId, actor, null, now, transition);
        auditEventPublisher.publish(new AuditRecordRequestedEvent(operationId, (short) 1, actor,
                AuditEntityType.APPROVED_OFFER, savedOffer.id(), AuditAction.OFFER_DECLINED, List.of(), now));
        salaryAdvanceReservationReleaseService.releaseReservationOnce(application, now, operationId, actor, (short) 2);
        return success(savedOffer, now);
    }

    private boolean isIdempotentCurrentResult(ApprovedOffer offer, OfferResponseAction action) {
        return (action == OfferResponseAction.ACCEPT && offer.status() == ApprovedOfferStatus.ACCEPTED)
                || (action == OfferResponseAction.DECLINE && offer.status() == ApprovedOfferStatus.DECLINED);
    }

    private ApprovedOfferActionResult success(ApprovedOffer offer, LocalDateTime now) {
        return new ApprovedOfferActionResult(ApprovedOfferActionOutcome.SUCCESS, approvedOfferMapper.toDto(offer, now));
    }

    private void assertOwnApplication(LoanApplication application, UUID customerId) {
        if (!application.customerId().equals(customerId)) {
            throw new AuthorizationException(
                    "ACCESS_DENIED",
                    "Loan application does not belong to the authenticated customer."
            );
        }
    }

    private BusinessStateConflictException offerActionConflict() {
        return new BusinessStateConflictException(
                "OFFER_ACTION_CONFLICT",
                "Approved offer action conflicts with the current offer state."
        );
    }

    private enum OfferResponseAction {
        ACCEPT,
        DECLINE
    }
}
