package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.DocumentReviewDto;
import com.meridian.platform.document.application.dto.ReviewDocumentCommand;
import com.meridian.platform.document.application.port.in.ReviewDocumentUseCase;
import com.meridian.platform.document.application.port.out.DocumentChecklistRepository;
import com.meridian.platform.document.application.port.out.DocumentRepository;
import com.meridian.platform.document.application.port.out.LoanDocumentReviewCorrectionPort;
import com.meridian.platform.document.application.port.out.LoanDocumentWorkflowPort;
import com.meridian.platform.document.domain.model.DocumentChecklist;
import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentChecklistStage;
import com.meridian.platform.document.domain.model.DocumentReviewDecision;
import com.meridian.platform.document.domain.model.DocumentReviewOutcome;
import com.meridian.platform.document.domain.model.StoredDocument;
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
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class ReviewDocumentService implements ReviewDocumentUseCase {

    private final LoanDocumentWorkflowPort workflowPort;
    private final DocumentChecklistRepository checklistRepository;
    private final DocumentRepository documentRepository;
    private final LoanDocumentReviewCorrectionPort correctionPort;
    private final BusinessAuditPublisher auditPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public ReviewDocumentService(
            LoanDocumentWorkflowPort workflowPort,
            DocumentChecklistRepository checklistRepository,
            DocumentRepository documentRepository,
            LoanDocumentReviewCorrectionPort correctionPort,
            BusinessAuditPublisher auditPublisher,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.workflowPort = workflowPort;
        this.checklistRepository = checklistRepository;
        this.documentRepository = documentRepository;
        this.correctionPort = correctionPort;
        this.auditPublisher = auditPublisher;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DocumentReviewDto review(ReviewDocumentCommand command) {
        validateCommand(command);
        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        if (!currentUser.userId().equals(command.reviewerUserId())) {
            throw new AuthorizationException("DOCUMENT_REVIEW_DENIED", "Document reviewer does not match authentication.");
        }
        if (command.outcome() == DocumentReviewOutcome.WAIVE_DOCUMENT && !command.waiverAuthorized()) {
            throw new AuthorizationException(
                    "DOCUMENT_WAIVER_DENIED",
                    "Document waiver requires explicit waiver authority."
            );
        }
        LocalDateTime now = LocalDateTime.now(clock);
        BusinessOperationContext operation = BusinessOperationContext.user(
                UUID.randomUUID(),
                currentUser.userId(),
                now
        );

        workflowPort.lock(command.loanApplicationId());
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
                    "DOCUMENT_REVIEW_DENIED",
                    "Document checklist item does not belong to this Loan Application."
            );
        }
        StoredDocument document = documentRepository.findDocumentByChecklistItemIdForUpdate(item.id())
                .orElseThrow(() -> new BusinessStateConflictException(
                        "DOCUMENT_UPLOAD_REQUIRED",
                        "A current document upload is required before review."
                ));

        DocumentReviewDecision existing = documentRepository
                .findReviewDecisionByReviewRequestId(command.reviewRequestId())
                .orElse(null);
        if (existing != null) {
            if (!existing.sameLogicalReview(
                    item.id(),
                    command.documentVersionId(),
                    command.outcome(),
                    command.waiverReasonCode(),
                    command.correctionReasonCode() == null ? null : command.correctionReasonCode().name(),
                    command.customerInstruction(),
                    command.restrictedStaffNotes(),
                    currentUser.userId()
            )) {
                throw new BusinessStateConflictException(
                        "IDEMPOTENCY_KEY_REUSED",
                        "The request ID was already used for a different document review."
                );
            }
            return toDto(existing);
        }

        if (!Objects.equals(document.currentVersionId(), command.documentVersionId())) {
            throw new BusinessStateConflictException(
                    "STALE_DOCUMENT_VERSION",
                    "Only the current immutable document version can be reviewed."
            );
        }
        if (item.currentReviewDecisionId() != null) {
            throw new BusinessStateConflictException(
                    "DOCUMENT_ALREADY_REVIEWED",
                    "Document version already reviewed."
            );
        }

        DocumentReviewDecision decision = documentRepository.saveReviewDecision(new DocumentReviewDecision(
                UUID.randomUUID(),
                item.id(),
                command.documentVersionId(),
                command.reviewRequestId(),
                command.outcome(),
                command.waiverReasonCode(),
                command.correctionReasonCode() == null ? null : command.correctionReasonCode().name(),
                command.customerInstruction(),
                command.restrictedStaffNotes(),
                currentUser.userId(),
                now
        ));
        checklistRepository.saveItem(item.withCurrentReviewDecision(decision.id(), now));
        if (command.outcome() == DocumentReviewOutcome.REQUEST_REPLACEMENT) {
            correctionPort.requestCustomerReplacement(
                    command.loanApplicationId(),
                    item.id(),
                    decision.documentVersionId(),
                    command.correctionReasonCode(),
                    command.customerInstruction(),
                    operation
            );
        }
        auditPublisher.publish(BusinessAuditEvent.single(
                operation,
                new BusinessAuditEntry(
                        auditAction(command.outcome()),
                        BusinessAuditEntityType.DOCUMENT_REVIEW_DECISION,
                        decision.id(),
                        auditPayload(command, item, decision)
                )
        ));
        return toDto(decision);
    }

    private BusinessAuditPayload auditPayload(
            ReviewDocumentCommand command,
            DocumentChecklistItem item,
            DocumentReviewDecision decision
    ) {
        BusinessAuditPayload.Builder payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, command.loanApplicationId())
                .put(BusinessAuditPayloadKey.DOCUMENT_CHECKLIST_ITEM_ID, item.id())
                .put(BusinessAuditPayloadKey.DOCUMENT_VERSION_ID, decision.documentVersionId())
                .put(BusinessAuditPayloadKey.DOCUMENT_REVIEW_OUTCOME, decision.outcome());
        if (decision.waiverReasonCode() != null) {
            payload.put(BusinessAuditPayloadKey.WAIVER_REASON_CODE, decision.waiverReasonCode());
        }
        return payload.build();
    }

    private BusinessAuditAction auditAction(DocumentReviewOutcome outcome) {
        return switch (outcome) {
            case ACCEPT_DOCUMENT -> BusinessAuditAction.DOCUMENT_REVIEW_ACCEPTED;
            case WAIVE_DOCUMENT -> BusinessAuditAction.DOCUMENT_WAIVED;
            case REQUEST_REPLACEMENT -> BusinessAuditAction.DOCUMENT_REPLACEMENT_REQUESTED;
        };
    }

    private void validateCommand(ReviewDocumentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.loanApplicationId(), "loanApplicationId must not be null");
        Objects.requireNonNull(command.checklistItemId(), "checklistItemId must not be null");
        Objects.requireNonNull(command.documentVersionId(), "documentVersionId must not be null");
        Objects.requireNonNull(command.reviewRequestId(), "reviewRequestId must not be null");
        Objects.requireNonNull(command.outcome(), "outcome must not be null");
        Objects.requireNonNull(command.reviewerUserId(), "reviewerUserId must not be null");
        if (command.outcome() == DocumentReviewOutcome.WAIVE_DOCUMENT
                && command.waiverReasonCode() == null) {
            throw new BusinessRuleViolationException(
                    "DOCUMENT_WAIVER_REASON_REQUIRED",
                    "A controlled waiver reason code is required."
            );
        }
        if (command.outcome() == DocumentReviewOutcome.REQUEST_REPLACEMENT) {
            if (command.correctionReasonCode()
                    != com.meridian.platform.approval.domain.model.CorrectionReasonCode
                    .DOCUMENT_REPLACEMENT_REQUIRED
                    || command.customerInstruction() == null
                    || command.customerInstruction().trim().isEmpty()
                    || command.customerInstruction().trim().length() > 500) {
                throw new BusinessRuleViolationException(
                        "INVALID_CORRECTION_PLAN",
                        "Replacement review requires a controlled reason and customer instruction."
                );
            }
        } else if (command.correctionReasonCode() != null || command.customerInstruction() != null) {
            throw new BusinessRuleViolationException(
                    "INVALID_CORRECTION_PLAN",
                    "Replacement correction fields are allowed only for REQUEST_REPLACEMENT."
            );
        }
    }

    private DocumentReviewDto toDto(DocumentReviewDecision decision) {
        return new DocumentReviewDto(
                decision.id(),
                decision.checklistItemId(),
                decision.documentVersionId(),
                decision.outcome().name(),
                decision.waiverReasonCode() == null ? null : decision.waiverReasonCode().name(),
                decision.decidedAt()
        );
    }
}
