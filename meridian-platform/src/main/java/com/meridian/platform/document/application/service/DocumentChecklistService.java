package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.port.out.DocumentChecklistRepository;
import com.meridian.platform.document.application.port.out.DocumentRepository;
import com.meridian.platform.document.domain.model.DocumentChecklist;
import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentChecklistReadiness;
import com.meridian.platform.document.domain.model.DocumentChecklistStage;
import com.meridian.platform.document.domain.model.DocumentReviewOutcome;
import com.meridian.platform.document.domain.model.DocumentRequirementStatus;
import com.meridian.platform.document.domain.model.DocumentType;
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
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class DocumentChecklistService implements LoanDocumentChecklistPort {

    private final DocumentChecklistRepository checklistRepository;
    private final DocumentRepository documentRepository;
    private final SalaryAdvanceDocumentChecklistResolver salaryAdvanceChecklistResolver;
    private final UnsecuredConsumerLoanDocumentChecklistResolver unsecuredConsumerLoanChecklistResolver;
    private final BusinessAuditPublisher businessAuditPublisher;

    public DocumentChecklistService(
            DocumentChecklistRepository checklistRepository,
            DocumentRepository documentRepository,
            SalaryAdvanceDocumentChecklistResolver salaryAdvanceChecklistResolver,
            UnsecuredConsumerLoanDocumentChecklistResolver unsecuredConsumerLoanChecklistResolver,
            BusinessAuditPublisher businessAuditPublisher
    ) {
        this.checklistRepository = checklistRepository;
        this.documentRepository = documentRepository;
        this.salaryAdvanceChecklistResolver = salaryAdvanceChecklistResolver;
        this.unsecuredConsumerLoanChecklistResolver = unsecuredConsumerLoanChecklistResolver;
        this.businessAuditPublisher = businessAuditPublisher;
    }

    @Override
    public SubmissionChecklistInitialState resolveSubmissionInitialState(ProductCode productCode) {
        List<DocumentChecklistItem> items = resolveChecklistItems(
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
        List<DocumentChecklistItem> items = resolveChecklistItems(
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

    @Override
    public ChecklistReadinessSnapshot readiness(UUID loanApplicationId) {
        requireChecklist(loanApplicationId);
        DocumentChecklistReadiness readiness = checklistRepository.findReadiness(
                loanApplicationId, DocumentChecklistStage.SUBMISSION
        );
        return new ChecklistReadinessSnapshot(readiness.uploadComplete(), readiness.processingReady());
    }

    @Override
    public UUID createRequiredItem(
            UUID loanApplicationId,
            DocumentType documentType,
            BusinessOperationContext operationContext
    ) {
        DocumentChecklist checklist = requireChecklist(loanApplicationId);
        if (checklist.items().stream().anyMatch(item -> item.documentType() == documentType)) {
            throw new BusinessStateConflictException(
                    "DOCUMENT_CHECKLIST_ITEM_EXISTS",
                    "The requested document checklist item already exists; use replacement instead."
            );
        }
        DocumentChecklistItem item = checklistRepository.saveItem(new DocumentChecklistItem(
                UUID.randomUUID(),
                checklist.id(),
                documentType,
                DocumentRequirementStatus.REQUIRED,
                null,
                operationContext.occurredAt(),
                operationContext.occurredAt()
        ));
        businessAuditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                new BusinessAuditEntry(
                        BusinessAuditAction.DOCUMENT_CHECKLIST_ITEM_CREATED,
                        BusinessAuditEntityType.DOCUMENT_CHECKLIST_ITEM,
                        item.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, loanApplicationId)
                                .put(BusinessAuditPayloadKey.DOCUMENT_CHECKLIST_ITEM_ID, item.id())
                                .put(BusinessAuditPayloadKey.DOCUMENT_TYPE, item.documentType())
                                .build()
                )
        ));
        return item.id();
    }

    @Override
    public UUID requireCurrentVersion(UUID loanApplicationId, UUID checklistItemId) {
        requireOwnedItem(loanApplicationId, checklistItemId);
        return documentRepository.findDocumentByChecklistItemId(checklistItemId)
                .filter(document -> document.currentVersionId() != null)
                .map(document -> document.currentVersionId())
                .orElseThrow(() -> new BusinessStateConflictException(
                        "DOCUMENT_UPLOAD_REQUIRED",
                        "A current document upload is required."
                ));
    }

    @Override
    public void requireCurrentVersion(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID expectedVersionId
    ) {
        UUID currentVersionId = requireCurrentVersion(loanApplicationId, checklistItemId);
        if (!currentVersionId.equals(expectedVersionId)) {
            throw new BusinessStateConflictException(
                    "STALE_DOCUMENT_VERSION",
                    "The expected document version is no longer current."
            );
        }
    }

    @Override
    public CurrentDocumentVersionSnapshot requireCurrentVersionSnapshot(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID expectedVersionId
    ) {
        DocumentChecklistItem item = requireOwnedItem(loanApplicationId, checklistItemId);
        UUID currentVersionId = requireCurrentVersion(loanApplicationId, checklistItemId);
        if (!currentVersionId.equals(expectedVersionId)) {
            throw new BusinessStateConflictException(
                    "STALE_DOCUMENT_VERSION",
                    "The expected document version is no longer current."
            );
        }
        return new CurrentDocumentVersionSnapshot(item.documentType(), currentVersionId);
    }

    @Override
    public boolean hasCurrentVersionDifferentFrom(UUID checklistItemId, UUID baselineVersionId) {
        return documentRepository.findDocumentByChecklistItemId(checklistItemId)
                .map(document -> document.currentVersionId() != null
                        && !document.currentVersionId().equals(baselineVersionId))
                .orElse(false);
    }

    @Override
    public boolean isVersionAcceptedOrWaived(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID documentVersionId
    ) {
        DocumentChecklistItem item = requireOwnedItem(loanApplicationId, checklistItemId);
        return documentRepository.findDocumentByChecklistItemId(checklistItemId)
                .filter(document -> documentVersionId.equals(document.currentVersionId()))
                .flatMap(document -> item.currentReviewDecisionId() == null
                        ? java.util.Optional.empty()
                        : documentRepository.findReviewDecisionById(item.currentReviewDecisionId()))
                .filter(decision -> decision.documentVersionId().equals(documentVersionId))
                .map(decision -> decision.outcome() == DocumentReviewOutcome.ACCEPT_DOCUMENT
                        || decision.outcome() == DocumentReviewOutcome.WAIVE_DOCUMENT)
                .orElse(false);
    }

    @Override
    public boolean isVersionReviewed(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID documentVersionId
    ) {
        DocumentChecklistItem item = requireOwnedItem(loanApplicationId, checklistItemId);
        return documentRepository.findDocumentByChecklistItemId(checklistItemId)
                .filter(document -> documentVersionId.equals(document.currentVersionId()))
                .flatMap(document -> item.currentReviewDecisionId() == null
                        ? java.util.Optional.empty()
                        : documentRepository.findReviewDecisionById(item.currentReviewDecisionId()))
                .map(decision -> decision.documentVersionId().equals(documentVersionId))
                .orElse(false);
    }

    private DocumentChecklist requireChecklist(UUID loanApplicationId) {
        return checklistRepository.findByLoanApplicationIdAndStage(
                loanApplicationId, DocumentChecklistStage.SUBMISSION
        ).orElseThrow(() -> new EntityNotFoundException(
                "DOCUMENT_CHECKLIST_NOT_FOUND",
                "Document checklist was not found."
        ));
    }

    private DocumentChecklistItem requireOwnedItem(UUID loanApplicationId, UUID checklistItemId) {
        DocumentChecklist checklist = requireChecklist(loanApplicationId);
        DocumentChecklistItem item = checklistRepository.findItemById(checklistItemId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "DOCUMENT_CHECKLIST_ITEM_NOT_FOUND",
                        "Document checklist item was not found."
                ));
        if (!item.checklistId().equals(checklist.id())) {
            throw new BusinessStateConflictException(
                    "DOCUMENT_CHECKLIST_ITEM_MISMATCH",
                    "Document checklist item does not belong to the Loan Application."
            );
        }
        return item;
    }

    private List<DocumentChecklistItem> resolveChecklistItems(
            UUID checklistId,
            ProductCode productCode,
            java.time.LocalDateTime createdAt
    ) {
        return switch (productCode) {
            case SALARY_ADVANCE -> salaryAdvanceChecklistResolver.resolve(checklistId, productCode, createdAt);
            case UNSECURED_CONSUMER_LOAN ->
                    unsecuredConsumerLoanChecklistResolver.resolve(checklistId, productCode, createdAt);
            default -> throw new IllegalArgumentException("Unsupported document checklist product.");
        };
    }
}
