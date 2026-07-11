package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ExpireApprovedOfferUseCase;
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
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActionActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ApprovedOfferExpiryService implements ExpireApprovedOfferUseCase {
    private final LoanApplicationRepository loanApplicationRepository;
    private final ApprovedOfferRepository approvedOfferRepository;
    private final SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService;
    private final LoanApplicationLifecycleHistoryRecorder historyRecorder;
    private final AuditEventPublisher auditEventPublisher;

    public ApprovedOfferExpiryService(
            LoanApplicationRepository loanApplicationRepository,
            ApprovedOfferRepository approvedOfferRepository,
            SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService,
            LoanApplicationLifecycleHistoryRecorder historyRecorder,
            AuditEventPublisher auditEventPublisher
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.approvedOfferRepository = approvedOfferRepository;
        this.salaryAdvanceReservationReleaseService = salaryAdvanceReservationReleaseService;
        this.historyRecorder = historyRecorder;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Override
    @Transactional
    public void expireDueOffer(UUID loanApplicationId, LocalDateTime now) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(loanApplicationId).orElseThrow(() ->
                new EntityNotFoundException("LOAN_APPLICATION_NOT_FOUND", "Loan application was not found."));
        ApprovedOffer approvedOffer = approvedOfferRepository.findByLoanApplicationIdForUpdate(loanApplicationId).orElseThrow(() ->
                new EntityNotFoundException("APPROVED_OFFER_NOT_FOUND", "Approved offer was not found for the loan application."));
        if (approvedOffer.status() != ApprovedOfferStatus.PENDING || !approvedOffer.isExpiredAt(now)) {
            return;
        }

        UUID operationId = UUID.randomUUID();
        ActionActor actor = ActionActor.system();
        LoanApplicationTransitionResult transition = loanApplication.expireApprovedOfferWithTransition();
        approvedOfferRepository.save(approvedOffer.expire(now));
        loanApplicationRepository.save(transition.loanApplication());
        historyRecorder.record(operationId, actor, null, now, transition);
        auditEventPublisher.publish(new AuditRecordRequestedEvent(
                operationId, (short) 1, actor, AuditEntityType.APPROVED_OFFER, approvedOffer.id(),
                AuditAction.OFFER_EXPIRED, List.of(), now));
        salaryAdvanceReservationReleaseService.releaseReservationOnce(loanApplication, now, operationId, actor, (short) 2);
    }
}
