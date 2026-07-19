package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
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
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher businessAuditPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public StartLoanApplicationReviewService(
            LoanApplicationRepository loanApplicationRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher businessAuditPublisher,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.documentChecklistPort = documentChecklistPort;
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

        if (!documentChecklistPort.isProcessingReady(loanApplicationId)) {
            throw new BusinessStateConflictException(
                    "LOAN_REVIEW_DOCUMENTS_NOT_READY",
                    "Loan Application documents are not ready for review."
            );
        }

        LoanApplicationTransitionResult transition = loanApplication.startReview();
        LoanApplication savedApplication = loanApplicationRepository.save(transition.loanApplication());
        transitionRecorder.record(operationContext, transition.facts(), null);
        businessAuditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                BusinessAuditEntry.of(
                        BusinessAuditAction.LOAN_REVIEW_STARTED,
                        BusinessAuditEntityType.LOAN_APPLICATION,
                        savedApplication.id()
                )
        ));

        return new LoanApplicationReviewDto(
                savedApplication.id(),
                savedApplication.status().name()
        );
    }
}
