package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.CompleteDocumentUploadsUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CompleteDocumentUploadsService implements CompleteDocumentUploadsUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher auditPublisher;

    public CompleteDocumentUploadsService(
            LoanApplicationRepository loanApplicationRepository,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher auditPublisher
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.transitionRecorder = transitionRecorder;
        this.auditPublisher = auditPublisher;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeDocumentUploads(
            UUID loanApplicationId,
            BusinessOperationContext operationContext
    ) {
        loanApplicationRepository.acquireWorkflowLock(loanApplicationId);
        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));
        if (loanApplication.status() != LoanApplicationStatus.DOCUMENTS_PENDING) {
            return;
        }
        LoanApplicationTransitionResult transition = loanApplication.completeDocumentUploads();
        loanApplicationRepository.save(transition.loanApplication());
        transitionRecorder.record(operationContext, transition.facts(), null);
        auditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                BusinessAuditEntry.of(
                        BusinessAuditAction.DOCUMENT_UPLOADS_COMPLETED,
                        BusinessAuditEntityType.LOAN_APPLICATION,
                        loanApplicationId
                )
        ));
    }
}
