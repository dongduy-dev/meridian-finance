package com.meridian.platform.loan.application.service;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Set;

@Service
public class PreReviewDocumentCorrectionService {
    private static final Set<DocumentType> UCL_DOCUMENT_TYPES = Set.of(
            DocumentType.INCOME_PROOF,
            DocumentType.BANK_STATEMENT,
            DocumentType.EMPLOYMENT_PROOF
    );

    private final LoanApplicationRepository applicationRepository;
    private final LoanCorrectionRepository correctionRepository;
    private final LoanDocumentChecklistPort documentChecklistPort;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher auditPublisher;

    public PreReviewDocumentCorrectionService(
            LoanApplicationRepository applicationRepository,
            LoanCorrectionRepository correctionRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher auditPublisher
    ) {
        this.applicationRepository = applicationRepository;
        this.correctionRepository = correctionRepository;
        this.documentChecklistPort = documentChecklistPort;
        this.transitionRecorder = transitionRecorder;
        this.auditPublisher = auditPublisher;
    }

    public void requestReplacement(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID baselineVersionId,
            CorrectionReasonCode reasonCode,
            String customerInstruction,
            BusinessOperationContext operation
    ) {
        if (reasonCode != CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED) {
            throw new BusinessRuleViolationException(
                    "INVALID_CORRECTION_PLAN", "Document replacement requires its controlled reason code.");
        }
        String instruction = customerInstruction == null ? null : customerInstruction.trim();
        if (instruction == null || instruction.isEmpty() || instruction.length() > 500) {
            throw new BusinessRuleViolationException(
                    "INVALID_CORRECTION_PLAN", "Customer replacement instruction is required.");
        }
        LoanDocumentChecklistPort.CurrentDocumentVersionSnapshot document =
                documentChecklistPort.requireCurrentVersionSnapshot(
                        loanApplicationId,
                        checklistItemId,
                        baselineVersionId
                );
        LoanApplication application = applicationRepository.findByIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND", "Loan Application was not found."));
        requireProductDocumentCompatibility(application.productCode(), document.documentType());
        var activeRequest = correctionRepository.findActiveRequestByApplicationIdForUpdate(loanApplicationId);
        if (activeRequest.isPresent()) {
            LoanCorrectionRequest request = activeRequest.get();
            var tasks = correctionRepository.findTasksByRequestIdForUpdate(request.id());
            if (tasks.stream().anyMatch(task ->
                    task.scope() == LoanCorrectionScope.DOCUMENT_REPLACEMENT
                            && checklistItemId.equals(task.checklistItemId())
                            && baselineVersionId.equals(task.baselineDocumentVersionId()))) {
                throw new BusinessStateConflictException(
                        "DOCUMENT_REPLACEMENT_ALREADY_REQUESTED",
                        "A replacement task already exists for this document version."
                );
            }
            correctionRepository.saveRequest(request.reopen());
            correctionRepository.saveTask(new LoanCorrectionTask(
                    UUID.randomUUID(), request.id(), correctionRepository.nextTaskSequence(request.id()),
                    LoanCorrectionResponsibility.CUSTOMER, LoanCorrectionScope.DOCUMENT_REPLACEMENT,
                    document.documentType(), false, checklistItemId, baselineVersionId,
                    instruction, null, LoanCorrectionTaskStatus.OPEN, null, null, null,
                    operation.occurredAt()
            ));
            return;
        }
        LoanApplicationTransitionResult transition = application.requestDocumentReplacementCorrection();

        LoanCorrectionRequest request = correctionRepository.saveRequest(new LoanCorrectionRequest(
                UUID.randomUUID(), loanApplicationId, null, "REQUEST_REPLACEMENT", reasonCode,
                operation.actorUserId(), LoanCorrectionRequestStatus.OPEN, null,
                operation.occurredAt(), null, null
        ));
        correctionRepository.saveTask(new LoanCorrectionTask(
                UUID.randomUUID(), request.id(), 1, LoanCorrectionResponsibility.CUSTOMER,
                LoanCorrectionScope.DOCUMENT_REPLACEMENT, document.documentType(),
                false, checklistItemId, baselineVersionId, instruction, null,
                LoanCorrectionTaskStatus.OPEN, null, null, null, operation.occurredAt()
        ));
        applicationRepository.save(transition.loanApplication());
        transitionRecorder.record(operation, transition.facts(), null);
        auditPublisher.publish(BusinessAuditEvent.single(operation, new BusinessAuditEntry(
                BusinessAuditAction.CORRECTION_REQUEST_CREATED,
                BusinessAuditEntityType.LOAN_CORRECTION_REQUEST,
                request.id(),
                BusinessAuditPayload.builder()
                        .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, loanApplicationId)
                        .put(BusinessAuditPayloadKey.CORRECTION_REQUEST_ID, request.id())
                        .put(BusinessAuditPayloadKey.CORRECTION_REASON_CODE, reasonCode)
                        .build()
        )));
    }

    private void requireProductDocumentCompatibility(
            ProductCode productCode,
            DocumentType documentType
    ) {
        boolean valid = switch (productCode) {
            case SALARY_ADVANCE -> documentType == DocumentType.RECENT_PAYSLIP;
            case UNSECURED_CONSUMER_LOAN -> UCL_DOCUMENT_TYPES.contains(documentType);
            case COLLATERAL_LOAN -> false;
        };
        if (!valid) {
            throw new BusinessRuleViolationException(
                    "INVALID_CORRECTION_PLAN",
                    "Document replacement is not valid for the Loan Application product."
            );
        }
    }
}
