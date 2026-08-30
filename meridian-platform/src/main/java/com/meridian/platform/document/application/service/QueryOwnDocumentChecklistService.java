package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.CustomerDocumentChecklistDto;
import com.meridian.platform.document.application.dto.DocumentVersionDto;
import com.meridian.platform.document.application.port.in.QueryOwnDocumentChecklistUseCase;
import com.meridian.platform.document.application.port.out.DocumentChecklistRepository;
import com.meridian.platform.document.application.port.out.DocumentRepository;
import com.meridian.platform.document.application.port.out.LoanDocumentWorkflowPort;
import com.meridian.platform.document.domain.model.DocumentChecklist;
import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentChecklistItemState;
import com.meridian.platform.document.domain.model.DocumentChecklistReadiness;
import com.meridian.platform.document.domain.model.DocumentChecklistStage;
import com.meridian.platform.document.domain.model.DocumentReviewOutcome;
import com.meridian.platform.document.domain.model.DocumentVersion;
import com.meridian.platform.document.domain.model.StoredDocument;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class QueryOwnDocumentChecklistService implements QueryOwnDocumentChecklistUseCase {

    private final LoanDocumentWorkflowPort workflows;
    private final DocumentChecklistRepository checklists;
    private final DocumentRepository documents;
    private final CurrentUserProvider currentUserProvider;

    public QueryOwnDocumentChecklistService(
            LoanDocumentWorkflowPort workflows,
            DocumentChecklistRepository checklists,
            DocumentRepository documents,
            CurrentUserProvider currentUserProvider
    ) {
        this.workflows = workflows;
        this.checklists = checklists;
        this.documents = documents;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDocumentChecklistDto query(UUID loanApplicationId) {
        AuthenticatedUser actor = currentUserProvider.currentUser();
        if (actor.optionalCustomerId().isEmpty() || !actor.hasPermission("document:read:own")) {
            throw new AuthorizationException(
                    "DOCUMENT_ACCESS_DENIED",
                    "Customer document access is denied."
            );
        }
        LoanDocumentWorkflowPort.LoanDocumentWorkflowSnapshot workflow;
        try {
            workflow = workflows.find(loanApplicationId);
        } catch (EntityNotFoundException exception) {
            throw notFound();
        }
        if (!workflow.customerId().equals(actor.requireCustomerId())) {
            throw notFound();
        }
        DocumentChecklist checklist = checklists.findByLoanApplicationIdAndStage(
                        loanApplicationId,
                        DocumentChecklistStage.SUBMISSION
                )
                .orElseThrow(QueryOwnDocumentChecklistService::notFound);

        List<ItemProjection> projections = checklist.items().stream()
                .map(this::toItem)
                .toList();
        DocumentChecklistReadiness readiness = DocumentChecklistReadiness.from(
                projections.stream().map(ItemProjection::state).toList()
        );
        return new CustomerDocumentChecklistDto(
                checklist.id(),
                checklist.loanApplicationId(),
                checklist.stage().name(),
                readiness.uploadComplete(),
                readiness.processingReady(),
                projections.stream().map(ItemProjection::dto).toList()
        );
    }

    private ItemProjection toItem(DocumentChecklistItem item) {
        StoredDocument document = documents.findDocumentByChecklistItemId(item.id()).orElse(null);
        DocumentVersion version = document == null || document.currentVersionId() == null
                ? null
                : documents.findVersionById(document.currentVersionId())
                .orElseThrow(QueryOwnDocumentChecklistService::stateConflict);
        DocumentReviewOutcome outcome = item.currentReviewDecisionId() == null
                ? null
                : documents.findReviewDecisionById(item.currentReviewDecisionId())
                .filter(decision -> version != null && decision.documentVersionId().equals(version.id()))
                .map(decision -> decision.outcome())
                .orElse(null);
        DocumentChecklistItemState state = new DocumentChecklistItemState(
                item.id(),
                item.requirementStatus(),
                version == null ? null : version.id(),
                outcome
        );
        return new ItemProjection(state, new CustomerDocumentChecklistDto.ChecklistItemDto(
                item.id(),
                item.documentType().name(),
                item.requirementStatus().name(),
                customerStatus(version, outcome),
                state.uploadComplete(),
                state.processingReady(),
                version == null ? null : new DocumentVersionDto(
                        version.id(),
                        item.id(),
                        version.versionNumber(),
                        version.originalFilename(),
                        version.detectedMimeType(),
                        version.byteSize(),
                        version.uploadedAt()
                )
        ));
    }

    private static String customerStatus(DocumentVersion version, DocumentReviewOutcome outcome) {
        if (outcome == DocumentReviewOutcome.ACCEPT_DOCUMENT) {
            return "ACCEPTED";
        }
        if (outcome == DocumentReviewOutcome.WAIVE_DOCUMENT) {
            return "WAIVED";
        }
        if (outcome == DocumentReviewOutcome.REQUEST_REPLACEMENT) {
            return "REPLACEMENT_REQUESTED";
        }
        return version == null ? "NOT_UPLOADED" : "AWAITING_REVIEW";
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException(
                "DOCUMENT_CHECKLIST_NOT_FOUND",
                "Document checklist was not found."
        );
    }

    private static BusinessStateConflictException stateConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Document checklist evidence conflicts with existing state."
        );
    }

    private record ItemProjection(
            DocumentChecklistItemState state,
            CustomerDocumentChecklistDto.ChecklistItemDto dto
    ) {
    }
}
