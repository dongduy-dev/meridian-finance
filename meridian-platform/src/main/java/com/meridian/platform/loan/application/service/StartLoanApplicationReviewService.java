package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanVerification;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class StartLoanApplicationReviewService implements StartLoanApplicationReviewUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanDocumentChecklistPort documentChecklistPort;
    private final UnsecuredConsumerLoanVerificationRepository uclVerificationRepository;
    private final LoanReviewCycleRepository reviewCycleRepository;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher businessAuditPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public StartLoanApplicationReviewService(
            LoanApplicationRepository loanApplicationRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            UnsecuredConsumerLoanVerificationRepository uclVerificationRepository,
            LoanReviewCycleRepository reviewCycleRepository,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher businessAuditPublisher,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.documentChecklistPort = documentChecklistPort;
        this.uclVerificationRepository = uclVerificationRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.transitionRecorder = transitionRecorder;
        this.businessAuditPublisher = businessAuditPublisher;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    @Transactional
    public LoanApplicationReviewDto startReview(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");

        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        BusinessOperationContext operationContext = BusinessOperationContext.user(
                UUID.randomUUID(),
                currentUser.userId(),
                LocalDateTime.now(clock)
        );

        loanApplicationRepository.acquireWorkflowLock(loanApplicationId);
        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));

        requireProductReadyForReview(loanApplication);
        if (!documentChecklistPort.isProcessingReady(loanApplicationId)) {
            throw new BusinessStateConflictException(
                    "LOAN_REVIEW_DOCUMENTS_NOT_READY",
                    "Loan Application documents are not ready for review."
            );
        }

        LoanApplicationTransitionResult transition = loanApplication.startReview();
        LoanApplication savedApplication = loanApplicationRepository.save(transition.loanApplication());
        LoanApplicationReviewCycle reviewCycle = reviewCycleRepository.save(
                LoanApplicationReviewCycle.active(
                        UUID.randomUUID(),
                        savedApplication.id(),
                        reviewCycleRepository.nextCycleNumber(savedApplication.id()),
                        operationContext.occurredAt()
                )
        );
        transitionRecorder.record(operationContext, transition.facts(), null);
        businessAuditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                BusinessAuditEntry.of(
                        BusinessAuditAction.LOAN_REVIEW_STARTED,
                        BusinessAuditEntityType.LOAN_APPLICATION,
                        savedApplication.id()
                )
        ));
        businessAuditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                new BusinessAuditEntry(
                        BusinessAuditAction.REVIEW_CYCLE_CREATED,
                        BusinessAuditEntityType.LOAN_REVIEW_CYCLE,
                        reviewCycle.id(),
                        com.meridian.platform.shared.domain.audit.BusinessAuditPayload.builder()
                                .put(com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey.LOAN_APPLICATION_ID,
                                        savedApplication.id())
                                .put(com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey.REVIEW_CYCLE_ID,
                                        reviewCycle.id())
                                .put(com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey.REVIEW_CYCLE_STATUS,
                                        reviewCycle.status())
                                .build()
                )
        ));

        return new LoanApplicationReviewDto(
                savedApplication.id(),
                savedApplication.status().name(),
                reviewCycle.id()
        );
    }

    private void requireProductReadyForReview(LoanApplication loanApplication) {
        if (loanApplication.productCode() != ProductCode.UNSECURED_CONSUMER_LOAN) {
            return;
        }

        UnsecuredConsumerLoanVerification verification = uclVerificationRepository
                .findLatestByLoanApplicationId(loanApplication.id())
                .orElseThrow(() -> new BusinessStateConflictException(
                        "UCL_VERIFICATION_REQUIRED",
                        "Unsecured Consumer Loan verification evidence is required before review."
                ));

        switch (verification.productVerificationResult()) {
            case VERIFIED -> {
            }
            case PENDING_MANUAL_REVIEW -> throw new BusinessRuleViolationException(
                    "PRODUCT_VERIFICATION_PENDING",
                    "Unsecured Consumer Loan verification must complete before review."
            );
            case FAILED -> throw new BusinessRuleViolationException(
                    "PRODUCT_VERIFICATION_FAILED",
                    "Unsecured Consumer Loan verification failed."
            );
            case REQUIRES_MORE_INFORMATION -> throw new BusinessRuleViolationException(
                    "PRODUCT_VERIFICATION_REQUIRES_MORE_INFORMATION",
                    "Unsecured Consumer Loan verification requires more information."
            );
        }
    }
}
