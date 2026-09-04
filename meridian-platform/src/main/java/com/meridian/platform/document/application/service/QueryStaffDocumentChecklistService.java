package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.StaffDocumentChecklistDto;
import com.meridian.platform.document.application.port.in.QueryStaffDocumentChecklistUseCase;
import com.meridian.platform.document.application.port.out.DocumentChecklistRepository;
import com.meridian.platform.document.application.port.out.DocumentRepository;
import com.meridian.platform.document.application.port.out.LoanDocumentWorkflowPort;
import com.meridian.platform.document.domain.model.DocumentChecklist;
import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentChecklistItemState;
import com.meridian.platform.document.domain.model.DocumentChecklistReadiness;
import com.meridian.platform.document.domain.model.DocumentChecklistStage;
import com.meridian.platform.document.domain.model.DocumentReviewDecision;
import com.meridian.platform.document.domain.model.DocumentReviewOutcome;
import com.meridian.platform.document.domain.model.DocumentVersion;
import com.meridian.platform.document.domain.model.StoredDocument;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class QueryStaffDocumentChecklistService implements QueryStaffDocumentChecklistUseCase {
    private final LoanDocumentWorkflowPort workflows;
    private final DocumentChecklistRepository checklists;
    private final DocumentRepository documents;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffDocumentChecklistService(
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
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffDocumentChecklistDto query(UUID loanApplicationId) {
        requireAuthority(currentUserProvider.currentUser());
        LoanDocumentWorkflowPort.LoanDocumentWorkflowSnapshot workflow;
        try {
            workflow = workflows.find(loanApplicationId);
        } catch (EntityNotFoundException exception) {
            throw notFound();
        }
        DocumentChecklist checklist = checklists.findByLoanApplicationIdAndStage(
                        loanApplicationId, DocumentChecklistStage.SUBMISSION)
                .orElseThrow(QueryStaffDocumentChecklistService::notFound);
        List<ItemProjection> projections = checklist.items().stream().map(this::toItem).toList();
        DocumentChecklistReadiness readiness = DocumentChecklistReadiness.from(
                projections.stream().map(ItemProjection::state).toList());
        return new StaffDocumentChecklistDto(
                loanApplicationId,
                workflow.status().name(),
                checklist.stage().name(),
                readiness.uploadComplete(),
                readiness.processingReady(),
                projections.stream().map(ItemProjection::dto).toList()
        );
    }

    private ItemProjection toItem(DocumentChecklistItem item) {
        StoredDocument document = documents.findDocumentByChecklistItemId(item.id()).orElse(null);
        List<DocumentVersion> versions = document == null
                ? List.of()
                : documents.findVersionsByDocumentId(document.id());
        DocumentVersion currentVersion = document == null || document.currentVersionId() == null
                ? null
                : versions.stream().filter(version -> version.id().equals(document.currentVersionId()))
                .findFirst().orElseThrow(QueryStaffDocumentChecklistService::stateConflict);
        List<DocumentReviewDecision> reviews = documents.findReviewDecisionsByChecklistItemId(item.id());
        if (reviews.stream().anyMatch(review -> versions.stream()
                .noneMatch(version -> version.id().equals(review.documentVersionId())))) {
            throw stateConflict();
        }
        DocumentReviewDecision currentReview = item.currentReviewDecisionId() == null
                ? null
                : reviews.stream().filter(review -> review.id().equals(item.currentReviewDecisionId()))
                .filter(review -> currentVersion != null
                        && review.documentVersionId().equals(currentVersion.id()))
                .findFirst().orElseThrow(QueryStaffDocumentChecklistService::stateConflict);
        DocumentReviewOutcome outcome = currentReview == null ? null : currentReview.outcome();
        DocumentChecklistItemState state = new DocumentChecklistItemState(
                item.id(), item.requirementStatus(),
                currentVersion == null ? null : currentVersion.id(), outcome);
        return new ItemProjection(state, new StaffDocumentChecklistDto.ChecklistItemDto(
                item.id(), item.documentType().name(), item.requirementStatus().name(),
                evidenceStatus(currentVersion, outcome), state.uploadComplete(), state.processingReady(),
                currentVersion == null ? null : toVersion(currentVersion),
                versions.stream().map(QueryStaffDocumentChecklistService::toVersion).toList(),
                reviews.stream().map(QueryStaffDocumentChecklistService::toReview).toList()
        ));
    }

    private static StaffDocumentChecklistDto.VersionDto toVersion(DocumentVersion version) {
        return new StaffDocumentChecklistDto.VersionDto(
                version.id(), version.versionNumber(), version.originalFilename(),
                version.detectedMimeType(), version.byteSize(), version.uploadedAt());
    }

    private static StaffDocumentChecklistDto.ReviewDto toReview(DocumentReviewDecision review) {
        return new StaffDocumentChecklistDto.ReviewDto(
                review.documentVersionId(), review.outcome().name(),
                review.waiverReasonCode() == null ? null : review.waiverReasonCode().name(),
                review.decidedAt());
    }

    private static String evidenceStatus(DocumentVersion version, DocumentReviewOutcome outcome) {
        if (outcome == DocumentReviewOutcome.ACCEPT_DOCUMENT) return "ACCEPTED";
        if (outcome == DocumentReviewOutcome.WAIVE_DOCUMENT) return "WAIVED";
        if (outcome == DocumentReviewOutcome.REQUEST_REPLACEMENT) return "REPLACEMENT_REQUESTED";
        return version == null ? "NOT_UPLOADED" : "AWAITING_REVIEW";
    }

    private static void requireAuthority(AuthenticatedUser actor) {
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("document:review")) {
            throw new AuthorizationException("DOCUMENT_ACCESS_DENIED", "Staff document access is denied.");
        }
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException("DOCUMENT_CHECKLIST_NOT_FOUND", "Document checklist was not found.");
    }

    private static BusinessStateConflictException stateConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT", "Document checklist evidence conflicts with existing state.");
    }

    private record ItemProjection(DocumentChecklistItemState state, StaffDocumentChecklistDto.ChecklistItemDto dto) {
    }
}
