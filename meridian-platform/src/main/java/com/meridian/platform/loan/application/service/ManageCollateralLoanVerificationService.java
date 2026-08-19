package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.CollateralAssessmentSnapshotDto;
import com.meridian.platform.loan.application.dto.CollateralLoanVerificationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanVerificationStartDto;
import com.meridian.platform.loan.application.dto.CompleteCollateralLoanVerificationRequest;
import com.meridian.platform.loan.application.port.in.ManageCollateralLoanVerificationUseCase;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.Collateral;
import com.meridian.platform.loan.domain.model.CollateralLoanManualVerificationOutcome;
import com.meridian.platform.loan.domain.model.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ManageCollateralLoanVerificationService
        implements ManageCollateralLoanVerificationUseCase {

    private final LoanApplicationRepository applicationRepository;
    private final CollateralLoanVerificationRepository verificationRepository;
    private final CollateralRepository collateralRepository;
    private final LoanDocumentChecklistPort documentChecklistPort;
    private final CustomerCorrectionWorkflowService correctionWorkflowService;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher auditPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public ManageCollateralLoanVerificationService(
            LoanApplicationRepository applicationRepository,
            CollateralLoanVerificationRepository verificationRepository,
            CollateralRepository collateralRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            CustomerCorrectionWorkflowService correctionWorkflowService,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher auditPublisher,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.applicationRepository = applicationRepository;
        this.verificationRepository = verificationRepository;
        this.collateralRepository = collateralRepository;
        this.documentChecklistPort = documentChecklistPort;
        this.correctionWorkflowService = correctionWorkflowService;
        this.transitionRecorder = transitionRecorder;
        this.auditPublisher = auditPublisher;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CollateralLoanVerificationStartDto startManualVerification(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        BusinessOperationContext operationContext = currentOperation();

        applicationRepository.acquireWorkflowLock(loanApplicationId);
        LoanApplication application = requireApplicationForUpdate(loanApplicationId);
        requireCollateralLoan(application);
        LoanApplicationTransitionResult transition = application.startProductVerification();
        CollateralLoanVerification verification = requireVerificationForUpdate(loanApplicationId);
        requirePending(verification);
        requireProcessingReady(loanApplicationId);
        Collateral collateral = requireSingleCollateral(loanApplicationId);

        LoanApplication savedApplication = applicationRepository.save(transition.loanApplication());
        transitionRecorder.record(operationContext, transition.facts(), null);
        publishAudit(
                operationContext,
                BusinessAuditAction.COLLATERAL_LOAN_VERIFICATION_STARTED,
                savedApplication.id()
        );
        return toStartDto(savedApplication, verification, collateral);
    }

    @Override
    @Transactional
    public CollateralLoanVerificationDto completeManualVerification(
            UUID loanApplicationId,
            CompleteCollateralLoanVerificationRequest request
    ) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        validateCompletionRequest(request);
        BusinessOperationContext operationContext = currentOperation();

        applicationRepository.acquireWorkflowLock(loanApplicationId);
        LoanApplication application = requireApplicationForUpdate(loanApplicationId);
        requireCollateralLoan(application);
        CollateralLoanVerification verification = requireVerificationForUpdate(loanApplicationId);
        if (!verification.id().equals(request.expectedVerificationId())) {
            throw new BusinessStateConflictException(
                    "STALE_COLLATERAL_VERIFICATION",
                    "The expected Collateral Loan verification cycle is no longer authoritative."
            );
        }
        requirePending(verification);
        requireProcessingReady(loanApplicationId);
        CollateralLoanVerification completedVerification = verification.completeManualReview(
                request.outcome(),
                operationContext.actorUserId(),
                operationContext.occurredAt(),
                request.assessmentNote()
        );
        LoanApplicationTransitionResult transition = application.completeProductVerification(
                completedVerification.productVerificationResult()
        );

        if (request.outcome() == CollateralLoanManualVerificationOutcome.REQUIRES_MORE_INFORMATION) {
            correctionWorkflowService.createFromProductVerification(
                    application,
                    request.reasonCode(),
                    request.correctionPlan(),
                    operationContext
            );
        }

        CollateralLoanVerification savedVerification = verificationRepository.save(completedVerification);
        LoanApplication savedApplication = applicationRepository.save(transition.loanApplication());
        transitionRecorder.record(operationContext, transition.facts(), null);
        publishAudit(
                operationContext,
                BusinessAuditAction.COLLATERAL_LOAN_VERIFICATION_COMPLETED,
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

    private CollateralLoanVerification requireVerificationForUpdate(UUID loanApplicationId) {
        return verificationRepository.findLatestByLoanApplicationIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new BusinessStateConflictException(
                        "COLLATERAL_VERIFICATION_REQUIRED",
                        "Collateral Loan verification evidence is required."
                ));
    }

    private Collateral requireSingleCollateral(UUID loanApplicationId) {
        List<Collateral> collaterals = collateralRepository.findByLoanApplicationId(loanApplicationId);
        if (collaterals.size() != 1) {
            throw new BusinessStateConflictException(
                    "SYSTEM_STATE_CONFLICT",
                    "Collateral Loan application evidence is inconsistent."
            );
        }
        return collaterals.getFirst();
    }

    private void validateCompletionRequest(CompleteCollateralLoanVerificationRequest request) {
        if (request.expectedVerificationId() == null || request.outcome() == null) {
            throw new BusinessRuleViolationException(
                    "INVALID_COLLATERAL_VERIFICATION_OUTCOME",
                    "An expected verification cycle and supported outcome are required."
            );
        }
        boolean hasReason = request.reasonCode() != null;
        boolean hasPlan = request.correctionPlan() != null;
        if (request.outcome() == CollateralLoanManualVerificationOutcome.REQUIRES_MORE_INFORMATION) {
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

    private void requireCollateralLoan(LoanApplication application) {
        if (application.productCode() != ProductCode.COLLATERAL_LOAN
                || application.productType() != ProductType.SECURED) {
            throw new BusinessRuleViolationException(
                    "COLLATERAL_VERIFICATION_NOT_APPLICABLE",
                    "Manual Collateral Loan verification applies only to that product."
            );
        }
    }

    private void requirePending(CollateralLoanVerification verification) {
        if (verification.productVerificationResult() != ProductVerificationResult.PENDING_MANUAL_REVIEW) {
            throw new BusinessStateConflictException(
                    "PRODUCT_VERIFICATION_NOT_PENDING",
                    "Collateral Loan verification is no longer pending manual review."
            );
        }
    }

    private void requireProcessingReady(UUID loanApplicationId) {
        if (!documentChecklistPort.isProcessingReady(loanApplicationId)) {
            throw new BusinessStateConflictException(
                    "COLLATERAL_VERIFICATION_DOCUMENTS_NOT_READY",
                    "Collateral Loan documents are not processing-ready."
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

    private CollateralLoanVerificationStartDto toStartDto(
            LoanApplication application,
            CollateralLoanVerification verification,
            Collateral collateral
    ) {
        return new CollateralLoanVerificationStartDto(
                verification.id(),
                application.id(),
                application.status().name(),
                verification.productVerificationResult().name(),
                new CollateralAssessmentSnapshotDto(
                        collateral.collateralType().name(),
                        collateral.description(),
                        collateral.estimatedValue(),
                        collateral.ownershipStatus(),
                        collateral.conditionNote()
                )
        );
    }

    private CollateralLoanVerificationDto toDto(
            LoanApplication application,
            CollateralLoanVerification verification
    ) {
        return new CollateralLoanVerificationDto(
                verification.id(),
                application.id(),
                application.status().name(),
                verification.productVerificationResult().name(),
                verification.reviewedAt()
        );
    }
}
