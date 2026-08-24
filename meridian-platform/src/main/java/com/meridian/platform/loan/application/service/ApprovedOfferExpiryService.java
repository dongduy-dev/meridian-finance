package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.service.salaryadvance.SalaryAdvanceReservationReleaseService;

import com.meridian.platform.loan.application.port.in.ExpireApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.salaryadvance.ReservationReleaseTrigger;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.audit.ExpiryDiscoveryTrigger;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class ApprovedOfferExpiryService implements ExpireApprovedOfferUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final ApprovedOfferRepository approvedOfferRepository;
    private final SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher businessAuditPublisher;

    public ApprovedOfferExpiryService(
            LoanApplicationRepository loanApplicationRepository,
            ApprovedOfferRepository approvedOfferRepository,
            SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher businessAuditPublisher
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.approvedOfferRepository = approvedOfferRepository;
        this.salaryAdvanceReservationReleaseService = salaryAdvanceReservationReleaseService;
        this.transitionRecorder = transitionRecorder;
        this.businessAuditPublisher = businessAuditPublisher;
    }

    @Override
    @Transactional
    public void expireDueOffer(
            UUID loanApplicationId,
            BusinessOperationContext operationContext,
            ExpiryDiscoveryTrigger discoveryTrigger
    ) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(operationContext, "operationContext must not be null");
        Objects.requireNonNull(discoveryTrigger, "discoveryTrigger must not be null");
        validateScheduledOperationContext(operationContext, discoveryTrigger);
        LocalDateTime now = operationContext.occurredAt();

        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));

        ApprovedOffer approvedOffer = approvedOfferRepository.findByLoanApplicationIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "APPROVED_OFFER_NOT_FOUND",
                        "Approved offer was not found for the loan application."
                ));

        if (approvedOffer.status() != ApprovedOfferStatus.PENDING || !approvedOffer.isExpiredAt(now)) {
            return;
        }

        ApprovedOffer expiredOffer = approvedOffer.expire(now);
        LoanApplicationTransitionResult transition = loanApplication.expireApprovedOffer();
        ApprovedOffer savedOffer = approvedOfferRepository.save(expiredOffer);
        loanApplicationRepository.save(transition.loanApplication());
        transitionRecorder.record(operationContext, transition.facts(), null);
        businessAuditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                new BusinessAuditEntry(
                        BusinessAuditAction.OFFER_EXPIRED,
                        BusinessAuditEntityType.APPROVED_OFFER,
                        savedOffer.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, loanApplicationId)
                                .put(BusinessAuditPayloadKey.OFFER_STATUS, savedOffer.status())
                                .put(BusinessAuditPayloadKey.EXPIRY_DISCOVERY_TRIGGER, discoveryTrigger)
                                .build()
                )
        ));
        salaryAdvanceReservationReleaseService.releaseReservationOnce(
                loanApplication,
                operationContext,
                ReservationReleaseTrigger.OFFER_EXPIRY
        );
    }

    private void validateScheduledOperationContext(
            BusinessOperationContext operationContext,
            ExpiryDiscoveryTrigger discoveryTrigger
    ) {
        if (discoveryTrigger == ExpiryDiscoveryTrigger.SCHEDULED_SCAN
                && (operationContext.actorType() != ActorType.SYSTEM || operationContext.actorUserId() != null)) {
            throw new BusinessRuleViolationException(
                    "INVALID_OPERATION_CONTEXT",
                    "Scheduled expiry operation context must use a SYSTEM actor."
            );
        }
    }
}
