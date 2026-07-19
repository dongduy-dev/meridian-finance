package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.DocumentContentDto;
import com.meridian.platform.document.application.port.in.ReadDocumentContentUseCase;
import com.meridian.platform.document.application.port.out.DocumentChecklistRepository;
import com.meridian.platform.document.application.port.out.DocumentRepository;
import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.application.port.out.LoanDocumentWorkflowPort;
import com.meridian.platform.document.domain.model.DocumentChecklist;
import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentChecklistStage;
import com.meridian.platform.document.domain.model.DocumentVersion;
import com.meridian.platform.document.domain.model.StoredDocument;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReadDocumentContentService implements ReadDocumentContentUseCase {

    private final LoanDocumentWorkflowPort workflowPort;
    private final DocumentChecklistRepository checklistRepository;
    private final DocumentRepository documentRepository;
    private final DocumentStoragePort storagePort;
    private final CurrentUserProvider currentUserProvider;

    public ReadDocumentContentService(
            LoanDocumentWorkflowPort workflowPort,
            DocumentChecklistRepository checklistRepository,
            DocumentRepository documentRepository,
            DocumentStoragePort storagePort,
            CurrentUserProvider currentUserProvider
    ) {
        this.workflowPort = workflowPort;
        this.checklistRepository = checklistRepository;
        this.documentRepository = documentRepository;
        this.storagePort = storagePort;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentContentDto read(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID documentVersionId
    ) {
        AuthenticatedUser user = currentUserProvider.currentUser();
        LoanDocumentWorkflowPort.LoanDocumentWorkflowSnapshot workflow =
                workflowPort.find(loanApplicationId);
        authorize(user, workflow.customerId());

        DocumentChecklist checklist = checklistRepository.findByLoanApplicationIdAndStage(
                        loanApplicationId, DocumentChecklistStage.SUBMISSION)
                .orElseThrow(() -> new EntityNotFoundException(
                        "DOCUMENT_CHECKLIST_NOT_FOUND", "Document checklist was not found."));
        DocumentChecklistItem item = checklistRepository.findItemById(checklistItemId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "DOCUMENT_CHECKLIST_ITEM_NOT_FOUND", "Document checklist item was not found."));
        if (!item.checklistId().equals(checklist.id())) {
            throw new AuthorizationException(
                    "DOCUMENT_ACCESS_DENIED",
                    "Document checklist item does not belong to this Loan Application."
            );
        }
        StoredDocument document = documentRepository.findDocumentByChecklistItemId(checklistItemId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "DOCUMENT_NOT_FOUND", "Document was not found."));
        DocumentVersion version = documentRepository.findVersionById(documentVersionId)
                .filter(candidate -> candidate.documentId().equals(document.id()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "DOCUMENT_VERSION_NOT_FOUND", "Document version was not found."));

        return new DocumentContentDto(
                version.originalFilename(),
                version.detectedMimeType(),
                version.byteSize(),
                storagePort.open(version.storageKey())
        );
    }

    private void authorize(AuthenticatedUser user, UUID ownerCustomerId) {
        if (user.optionalCustomerId().isPresent()) {
            if (!user.hasPermission("document:read:own")
                    || !ownerCustomerId.equals(user.requireCustomerId())) {
                throw new AuthorizationException(
                        "DOCUMENT_ACCESS_DENIED", "Customer cannot read this document.");
            }
            return;
        }
        if (!user.hasPermission("document:review")) {
            throw new AuthorizationException(
                    "DOCUMENT_ACCESS_DENIED", "Document review permission is required.");
        }
    }
}
