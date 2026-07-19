package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.DocumentVersionDto;
import com.meridian.platform.document.application.dto.UploadDocumentCommand;
import com.meridian.platform.document.application.event.DocumentUploadsCompletedEvent;
import com.meridian.platform.document.application.port.out.DocumentChecklistRepository;
import com.meridian.platform.document.application.port.out.DocumentRepository;
import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.application.port.out.DocumentWorkflowEventPublisher;
import com.meridian.platform.document.application.port.out.LoanDocumentWorkflowPort;
import com.meridian.platform.document.application.port.out.StagedDocument;
import com.meridian.platform.document.application.port.out.StoredObject;
import com.meridian.platform.document.domain.model.DocumentChecklist;
import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentChecklistReadiness;
import com.meridian.platform.document.domain.model.DocumentChecklistStage;
import com.meridian.platform.document.domain.model.DocumentUploaderActorType;
import com.meridian.platform.document.domain.model.DocumentVersion;
import com.meridian.platform.document.domain.model.StoredDocument;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class TransactionalDocumentUploadService {

    private final LoanDocumentWorkflowPort workflowPort;
    private final DocumentChecklistRepository checklistRepository;
    private final DocumentRepository documentRepository;
    private final DocumentStoragePort storagePort;
    private final DocumentWorkflowEventPublisher workflowEventPublisher;
    private final BusinessAuditPublisher auditPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public TransactionalDocumentUploadService(
            LoanDocumentWorkflowPort workflowPort,
            DocumentChecklistRepository checklistRepository,
            DocumentRepository documentRepository,
            DocumentStoragePort storagePort,
            DocumentWorkflowEventPublisher workflowEventPublisher,
            BusinessAuditPublisher auditPublisher,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.workflowPort = workflowPort;
        this.checklistRepository = checklistRepository;
        this.documentRepository = documentRepository;
        this.storagePort = storagePort;
        this.workflowEventPublisher = workflowEventPublisher;
        this.auditPublisher = auditPublisher;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Transactional
    public DocumentVersionDto store(UploadDocumentCommand command, StagedDocument staged) {
        validateCommand(command);
        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        validateAuthoritativeActor(command, currentUser);
        LocalDateTime now = LocalDateTime.now(clock);
        BusinessOperationContext operation = BusinessOperationContext.user(
                UUID.randomUUID(),
                currentUser.userId(),
                now
        );

        LoanDocumentWorkflowPort.LoanDocumentWorkflowSnapshot workflow = workflowPort.lock(
                command.loanApplicationId()
        );
        validateOwnership(command, workflow);
        DocumentChecklist checklist = checklistRepository.findByLoanApplicationIdAndStage(
                        command.loanApplicationId(),
                        DocumentChecklistStage.SUBMISSION
                )
                .orElseThrow(() -> new EntityNotFoundException(
                        "DOCUMENT_CHECKLIST_NOT_FOUND",
                        "Document checklist was not found."
                ));
        DocumentChecklistItem item = checklistRepository.findItemByIdForUpdate(command.checklistItemId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "DOCUMENT_CHECKLIST_ITEM_NOT_FOUND",
                        "Document checklist item was not found."
                ));
        if (!item.checklistId().equals(checklist.id())) {
            throw new AuthorizationException(
                    "DOCUMENT_ACCESS_DENIED",
                    "Document checklist item does not belong to this Loan Application."
            );
        }

        StoredDocument document = documentRepository
                .findDocumentByChecklistItemIdForUpdate(item.id())
                .orElseGet(() -> documentRepository.saveDocument(new StoredDocument(
                        UUID.randomUUID(),
                        item.id(),
                        null,
                        now,
                        now
                )));

        DocumentVersion idempotent = documentRepository.findVersionByUploadRequestId(command.uploadRequestId())
                .orElse(null);
        if (idempotent != null) {
            if (!idempotent.sameLogicalUpload(
                    document.id(),
                    command.expectedCurrentVersionId(),
                    staged.originalFilename(),
                    staged.declaredMimeType(),
                    staged.byteSize(),
                    staged.sha256Hex(),
                    currentUser.userId()
            )) {
                throw idempotencyReused();
            }
            return toDto(idempotent, item.id());
        }

        if (!Objects.equals(document.currentVersionId(), command.expectedCurrentVersionId())) {
            throw new BusinessStateConflictException(
                    "STALE_DOCUMENT_VERSION",
                    "The document current version changed before this upload was stored."
            );
        }

        int versionNumber = document.currentVersionId() == null
                ? 1
                : documentRepository.findVersionById(document.currentVersionId())
                        .orElseThrow(() -> new IllegalStateException("Current document version was not found."))
                        .versionNumber() + 1;

        StoredObject storedObject = storagePort.commit(staged);
        registerRollbackCleanup(storedObject.storageKey());
        DocumentVersion version = documentRepository.saveVersion(new DocumentVersion(
                UUID.randomUUID(),
                document.id(),
                versionNumber,
                command.uploadRequestId(),
                command.expectedCurrentVersionId(),
                staged.originalFilename(),
                staged.declaredMimeType(),
                staged.detectedMimeType(),
                staged.byteSize(),
                staged.sha256Hex(),
                storedObject.storageKey(),
                command.uploaderActorType(),
                currentUser.userId(),
                now
        ));

        documentRepository.saveDocument(document.withCurrentVersion(version.id(), now));
        checklistRepository.saveItem(item.withCurrentReviewDecision(null, now));
        auditPublisher.publish(BusinessAuditEvent.single(
                operation,
                new BusinessAuditEntry(
                        BusinessAuditAction.DOCUMENT_VERSION_UPLOADED,
                        BusinessAuditEntityType.DOCUMENT_VERSION,
                        version.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, command.loanApplicationId())
                                .put(BusinessAuditPayloadKey.DOCUMENT_CHECKLIST_ITEM_ID, item.id())
                                .put(BusinessAuditPayloadKey.DOCUMENT_VERSION_ID, version.id())
                                .put(BusinessAuditPayloadKey.DOCUMENT_TYPE, item.documentType())
                                .build()
                )
        ));

        DocumentChecklistReadiness readiness = checklistRepository.findReadiness(
                command.loanApplicationId(),
                DocumentChecklistStage.SUBMISSION
        );
        if (workflow.status() == LoanApplicationStatus.DOCUMENTS_PENDING && readiness.uploadComplete()) {
            workflowEventPublisher.publish(new DocumentUploadsCompletedEvent(
                    command.loanApplicationId(),
                    operation
            ));
        }
        return toDto(version, item.id());
    }

    private void validateCommand(UploadDocumentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.loanApplicationId(), "loanApplicationId must not be null");
        Objects.requireNonNull(command.checklistItemId(), "checklistItemId must not be null");
        Objects.requireNonNull(command.uploadRequestId(), "uploadRequestId must not be null");
        Objects.requireNonNull(command.uploaderActorType(), "uploaderActorType must not be null");
        Objects.requireNonNull(command.uploaderUserId(), "uploaderUserId must not be null");
    }

    private void validateAuthoritativeActor(UploadDocumentCommand command, AuthenticatedUser currentUser) {
        if (!currentUser.userId().equals(command.uploaderUserId())) {
            throw new AuthorizationException("DOCUMENT_ACCESS_DENIED", "Document actor does not match authentication.");
        }
        if (command.uploaderActorType() == DocumentUploaderActorType.CUSTOMER
                && !currentUser.requireCustomerId().equals(command.uploaderCustomerId())) {
            throw new AuthorizationException("DOCUMENT_ACCESS_DENIED", "Document customer does not match authentication.");
        }
    }

    private void validateOwnership(
            UploadDocumentCommand command,
            LoanDocumentWorkflowPort.LoanDocumentWorkflowSnapshot workflow
    ) {
        if (command.uploaderActorType() == DocumentUploaderActorType.CUSTOMER
                && !workflow.customerId().equals(command.uploaderCustomerId())) {
            throw new AuthorizationException(
                    "DOCUMENT_ACCESS_DENIED",
                    "Customer cannot upload documents for another Loan Application."
            );
        }
    }

    private void registerRollbackCleanup(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storagePort.deleteFinal(storageKey);
                }
            }
        });
    }

    private DocumentVersionDto toDto(DocumentVersion version, UUID checklistItemId) {
        return new DocumentVersionDto(
                version.id(),
                checklistItemId,
                version.versionNumber(),
                version.originalFilename(),
                version.detectedMimeType(),
                version.byteSize(),
                version.uploadedAt()
        );
    }

    private BusinessStateConflictException idempotencyReused() {
        return new BusinessStateConflictException(
                "IDEMPOTENCY_KEY_REUSED",
                "The request ID was already used for different document content."
        );
    }
}
