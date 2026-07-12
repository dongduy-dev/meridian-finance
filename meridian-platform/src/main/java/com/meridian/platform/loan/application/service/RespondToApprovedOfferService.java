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
import com.meridian.platform.loan.domain.model.ReservationReleaseTrigger;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.audit.ExpiryDiscoveryTrigger;
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
public class RespondToApprovedOfferService implements RespondToApprovedOfferUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final ApprovedOfferRepository approvedOfferRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher businessAuditPublisher;
    private final ApprovedOfferMapper approvedOfferMapper;
    private final Clock clock;

    public RespondToApprovedOfferService(
            LoanApplicationRepository loanApplicationRepository,
            ApprovedOfferRepository approvedOfferRepository,
            CurrentUserProvider currentUserProvider,
            SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher businessAuditPublisher,
            ApprovedOfferMapper approvedOfferMapper,
            Clock clock
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.approvedOfferRepository = approvedOfferRepository;
        this.currentUserProvider = currentUserProvider;
        this.salaryAdvanceReservationReleaseService = salaryAdvanceReservationReleaseService;
        this.transitionRecorder = transitionRecorder;
        this.businessAuditPublisher = businessAuditPublisher;
        this.approvedOfferMapper = approvedOfferMapper;
        this.clock = clock;
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
        Objects.requireNonNull(action, "action must not be null");

        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        UUID customerId = currentUser.requireCustomerId();
        LocalDateTime now = LocalDateTime.now(clock);
        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));
        assertOwnApplication(loanApplication, customerId);

        ApprovedOffer approvedOffer = approvedOfferRepository.findByLoanApplicationIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "APPROVED_OFFER_NOT_FOUND",
                        "Approved offer was not found for the loan application."
                ));

        if (isIdempotentCurrentResult(approvedOffer, action)) {
            return success(approvedOffer, now);
        }
        if (approvedOffer.status() != ApprovedOfferStatus.PENDING) {
            throw offerActionConflict();
        }
        if (approvedOffer.isExpiredAt(now)) {
            return expireFromCustomerAction(loanApplication, approvedOffer, now);
        }

        BusinessOperationContext userOperationContext = BusinessOperationContext.user(
                UUID.randomUUID(),
                currentUser.userId(),
                now
        );
        if (action == OfferResponseAction.ACCEPT) {
            ApprovedOffer acceptedOffer = approvedOffer.accept(now);
            LoanApplicationTransitionResult transition = loanApplication.acceptApprovedOffer();
            ApprovedOffer savedOffer = approvedOfferRepository.save(acceptedOffer);
            loanApplicationRepository.save(transition.loanApplication());
            transitionRecorder.record(userOperationContext, transition.facts(), null);
            auditOfferAction(
                    userOperationContext,
                    BusinessAuditAction.APPROVED_OFFER_ACCEPTED,
                    savedOffer,
                    null
            );
            return success(savedOffer, now);
        }

        ApprovedOffer declinedOffer = approvedOffer.decline(now);
        LoanApplicationTransitionResult transition = loanApplication.declineApprovedOffer();
        ApprovedOffer savedOffer = approvedOfferRepository.save(declinedOffer);
        loanApplicationRepository.save(transition.loanApplication());
        transitionRecorder.record(userOperationContext, transition.facts(), null);
        auditOfferAction(
                userOperationContext,
                BusinessAuditAction.APPROVED_OFFER_DECLINED,
                savedOffer,
                null
        );
        salaryAdvanceReservationReleaseService.releaseReservationOnce(
                loanApplication,
                userOperationContext,
                ReservationReleaseTrigger.CUSTOMER_DECLINE
        );
        return success(savedOffer, now);
    }

    private ApprovedOfferActionResult expireFromCustomerAction(
            LoanApplication loanApplication,
            ApprovedOffer approvedOffer,
            LocalDateTime now
    ) {
        BusinessOperationContext systemOperationContext = BusinessOperationContext.system(UUID.randomUUID(), now);
        ApprovedOffer expiredOffer = approvedOffer.expire(now);
        LoanApplicationTransitionResult transition = loanApplication.expireApprovedOffer();
        ApprovedOffer savedOffer = approvedOfferRepository.save(expiredOffer);
        loanApplicationRepository.save(transition.loanApplication());
        transitionRecorder.record(systemOperationContext, transition.facts(), null);
        auditOfferAction(
                systemOperationContext,
                BusinessAuditAction.OFFER_EXPIRED,
                savedOffer,
                ExpiryDiscoveryTrigger.CUSTOMER_ACTION
        );
        salaryAdvanceReservationReleaseService.releaseReservationOnce(
                loanApplication,
                systemOperationContext,
                ReservationReleaseTrigger.OFFER_EXPIRY
        );
        return new ApprovedOfferActionResult(
                ApprovedOfferActionOutcome.EXPIRED,
                approvedOfferMapper.toDto(savedOffer, now)
        );
    }

    private void auditOfferAction(
            BusinessOperationContext operationContext,
            BusinessAuditAction action,
            ApprovedOffer approvedOffer,
            ExpiryDiscoveryTrigger trigger
    ) {
        BusinessAuditPayload.Builder payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, approvedOffer.loanApplicationId())
                .put(BusinessAuditPayloadKey.OFFER_STATUS, approvedOffer.status());
        if (trigger != null) {
            payload.put(BusinessAuditPayloadKey.EXPIRY_DISCOVERY_TRIGGER, trigger);
        }
        businessAuditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                new BusinessAuditEntry(
                        action,
                        BusinessAuditEntityType.APPROVED_OFFER,
                        approvedOffer.id(),
                        payload.build()
                )
        ));
    }

    private boolean isIdempotentCurrentResult(ApprovedOffer approvedOffer, OfferResponseAction action) {
        return (action == OfferResponseAction.ACCEPT && approvedOffer.status() == ApprovedOfferStatus.ACCEPTED)
                || (action == OfferResponseAction.DECLINE && approvedOffer.status() == ApprovedOfferStatus.DECLINED);
    }

    private ApprovedOfferActionResult success(ApprovedOffer approvedOffer, LocalDateTime now) {
        return new ApprovedOfferActionResult(
                ApprovedOfferActionOutcome.SUCCESS,
                approvedOfferMapper.toDto(approvedOffer, now)
        );
    }

    private void assertOwnApplication(LoanApplication loanApplication, UUID customerId) {
        if (!loanApplication.customerId().equals(customerId)) {
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
