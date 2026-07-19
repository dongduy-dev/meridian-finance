package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.port.out.DocumentChecklistRepository;
import com.meridian.platform.document.domain.model.DocumentChecklist;
import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentChecklistReadiness;
import com.meridian.platform.document.domain.model.DocumentChecklistStage;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class DocumentChecklistService implements LoanDocumentChecklistPort {

    private final DocumentChecklistRepository checklistRepository;
    private final SalaryAdvanceDocumentChecklistResolver checklistResolver;
    private final BusinessAuditPublisher businessAuditPublisher;

    public DocumentChecklistService(
            DocumentChecklistRepository checklistRepository,
            SalaryAdvanceDocumentChecklistResolver checklistResolver,
            BusinessAuditPublisher businessAuditPublisher
    ) {
        this.checklistRepository = checklistRepository;
        this.checklistResolver = checklistResolver;
        this.businessAuditPublisher = businessAuditPublisher;
    }

    @Override
    public SubmissionChecklistInitialState resolveSubmissionInitialState(ProductCode productCode) {
        List<DocumentChecklistItem> items = checklistResolver.resolve(
                UUID.randomUUID(),
                productCode,
                java.time.LocalDateTime.MIN
        );
        return new SubmissionChecklistInitialState(items.isEmpty());
    }

    @Override
    public void createSubmissionChecklist(
            UUID loanApplicationId,
            ProductCode productCode,
            BusinessOperationContext operationContext
    ) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(productCode, "productCode must not be null");
        Objects.requireNonNull(operationContext, "operationContext must not be null");

        UUID checklistId = UUID.randomUUID();
        List<DocumentChecklistItem> items = checklistResolver.resolve(
                checklistId,
                productCode,
                operationContext.occurredAt()
        );
        DocumentChecklist saved = checklistRepository.save(new DocumentChecklist(
                checklistId,
                loanApplicationId,
                DocumentChecklistStage.SUBMISSION,
                items,
                operationContext.occurredAt()
        ));

        businessAuditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                new BusinessAuditEntry(
                        BusinessAuditAction.DOCUMENT_CHECKLIST_CREATED,
                        BusinessAuditEntityType.DOCUMENT_CHECKLIST,
                        saved.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, loanApplicationId)
                                .build()
                )
        ));
    }

    @Override
    public boolean isProcessingReady(UUID loanApplicationId) {
        if (checklistRepository.findByLoanApplicationIdAndStage(
                loanApplicationId,
                DocumentChecklistStage.SUBMISSION
        ).isEmpty()) {
            throw new BusinessStateConflictException(
                    "DOCUMENT_CHECKLIST_NOT_FOUND",
                    "Loan application document checklist was not found."
            );
        }
        DocumentChecklistReadiness readiness = checklistRepository.findReadiness(
                loanApplicationId,
                DocumentChecklistStage.SUBMISSION
        );
        return readiness.processingReady();
    }
}
