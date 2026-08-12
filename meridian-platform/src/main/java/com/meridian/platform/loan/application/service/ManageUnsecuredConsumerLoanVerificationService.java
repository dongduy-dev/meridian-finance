package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.CompleteUnsecuredConsumerLoanVerificationRequest;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanVerificationDto;
import com.meridian.platform.loan.application.port.in.ManageUnsecuredConsumerLoanVerificationUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanVerification;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanManualVerificationOutcome;
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
public class ManageUnsecuredConsumerLoanVerificationService
        implements ManageUnsecuredConsumerLoanVerificationUseCase {

    private final LoanApplicationRepository applicationRepository;
    private final UnsecuredConsumerLoanVerificationRepository verificationRepository;
    private final LoanDocumentChecklistPort documentChecklistPort;
    private final CustomerCorrectionWorkflowService correctionWorkflowService;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher auditPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public ManageUnsecuredConsumerLoanVerificationService(
            LoanApplicationRepository applicationRepository,
            UnsecuredConsumerLoanVerificationRepository verificationRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            CustomerCorrectionWorkflowService correctionWorkflowService,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher auditPublisher,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.applicationRepository = applicationRepository;
        this.verificationRepository = verificationRepository;
        this.documentChecklistPort = documentChecklistPort;
        this.correctionWorkflowService = correctionWorkflowService;
        this.transitionRecorder = transitionRecorder;
        this.auditPublisher = auditPublisher;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UnsecuredConsumerLoanVerificationDto startManualVerification(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        BusinessOperationContext operationContext = currentOperation();

        applicationRepository.acquireWorkflowLock(loanApplicationId);
        LoanApplication application = requireApplicationForUpdate(loanApplicationId);
        requireUnsecuredConsumerLoan(application);
        LoanApplicationTransitionResult transition = application.startProductVerification();
        UnsecuredConsumerLoanVerification verification = requireVerificationForUpdate(loanApplicationId);
        if (verification.productVerificationResult() != ProductVerificationResult.PENDING_MANUAL_REVIEW) {
            throw new BusinessStateConflictException(
                    "PRODUCT_VERIFICATION_NOT_PENDING",
                    "Unsecured Consumer Loan verification is no longer pending manual review."
            );
        }
        requireProcessingReady(loanApplicationId);

        LoanApplication savedApplication = applicationRepository.save(transition.loanApplication());
        transitionRecorder.record(operationContext, transition.facts(), null);
        publishAudit(
                operationContext,
                BusinessAuditAction.UNSECURED_CONSUMER_LOAN_VERIFICATION_STARTED,
                savedApplication.id()
        );
        return toDto(savedApplication, verification);
    }

    @Override
    @Transactional
    public UnsecuredConsumerLoanVerificationDto completeManualVerification(
            UUID loanApplicationId,
            CompleteUnsecuredConsumerLoanVerificationRequest request
    ) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        validateCompletionRequest(request);
        BusinessOperationContext operationContext = currentOperation();

        applicationRepository.acquireWorkflowLock(loanApplicationId);
        LoanApplication application = requireApplicationForUpdate(loanApplicationId);
        requireUnsecuredConsumerLoan(application);
        UnsecuredConsumerLoanVerification completedVerification = requireVerificationForUpdate(loanApplicationId)
                .completeManualReview(
                        request.outcome(),
                        operationContext.actorUserId(),
                        operationContext.occurredAt(),
                        request.assessmentNote()
                );
        LoanApplicationTransitionResult transition = application.completeProductVerification(
                completedVerification.productVerificationResult()
        );
        requireProcessingReady(loanApplicationId);

        if (request.outcome() == UnsecuredConsumerLoanManualVerificationOutcome.REQUIRES_MORE_INFORMATION) {
            correctionWorkflowService.createFromProductVerification(
                    application,
                    request.reasonCode(),
                    request.correctionPlan(),
                    operationContext
            );
        }

        UnsecuredConsumerLoanVerification savedVerification = verificationRepository.save(completedVerification);
        LoanApplication savedApplication = applicationRepository.save(transition.loanApplication());
        transitionRecorder.record(operationContext, transition.facts(), null);
        publishAudit(
                operationContext,
                BusinessAuditAction.UNSECURED_CONSUMER_LOAN_VERIFICATION_COMPLETED,
                savedApplication.id()
        );
        return toDto(savedApplication, savedVerification);
    }

    private BusinessOperationContext currentOperation() {
        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        return BusinessOperationContext.user(
                UUID.randomUUID(),
                currentUser.userId(),
                LocalDateTime.now(clock)
        );
    }

    private LoanApplication requireApplicationForUpdate(UUID loanApplicationId) {
        return applicationRepository.findByIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));
    }

    private UnsecuredConsumerLoanVerification requireVerificationForUpdate(UUID loanApplicationId) {
        return verificationRepository.findLatestByLoanApplicationIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new BusinessStateConflictException(
                        "UCL_VERIFICATION_REQUIRED",
                        "Unsecured Consumer Loan verification evidence is required."
                ));
    }

    private void validateCompletionRequest(
            CompleteUnsecuredConsumerLoanVerificationRequest request
    ) {
        if (request.outcome() == null) {
            throw new BusinessRuleViolationException(
                    "INVALID_UCL_VERIFICATION_OUTCOME",
                    "A supported manual verification outcome is required."
            );
        }
        boolean hasReason = request.reasonCode() != null;
        boolean hasPlan = request.correctionPlan() != null;
        if (request.outcome()
                == UnsecuredConsumerLoanManualVerificationOutcome.REQUIRES_MORE_INFORMATION) {
            if (!hasReason || !hasPlan) {
                throw new BusinessRuleViolationException(
                        "INVALID_CORRECTION_PLAN",
                        "More-information verification requires a controlled reason and correction plan."
                );
            }
            return;
        }
        if (hasReason || hasPlan) {
            throw new BusinessRuleViolationException(
                    "INVALID_CORRECTION_PLAN",
                    "Correction fields are allowed only for a more-information verification outcome."
            );
        }
    }

    private void requireUnsecuredConsumerLoan(LoanApplication application) {
        if (application.productCode() != ProductCode.UNSECURED_CONSUMER_LOAN
                || application.productType() != ProductType.UNSECURED) {
            throw new BusinessRuleViolationException(
                    "UCL_VERIFICATION_NOT_APPLICABLE",
                    "Manual Unsecured Consumer Loan verification applies only to that product."
            );
        }
    }

    private void requireProcessingReady(UUID loanApplicationId) {
        if (!documentChecklistPort.isProcessingReady(loanApplicationId)) {
            throw new BusinessStateConflictException(
                    "UCL_VERIFICATION_DOCUMENTS_NOT_READY",
                    "Unsecured Consumer Loan documents are not processing-ready."
            );
        }
    }

    private void publishAudit(
            BusinessOperationContext operationContext,
            BusinessAuditAction action,
            UUID loanApplicationId
    ) {
        auditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                BusinessAuditEntry.of(
                        action,
                        BusinessAuditEntityType.LOAN_APPLICATION,
                        loanApplicationId
                )
        ));
    }

    private UnsecuredConsumerLoanVerificationDto toDto(
            LoanApplication application,
            UnsecuredConsumerLoanVerification verification
    ) {
        return new UnsecuredConsumerLoanVerificationDto(
                application.id(),
                application.status().name(),
                verification.productVerificationResult().name(),
                verification.reviewedAt()
        );
    }
}
