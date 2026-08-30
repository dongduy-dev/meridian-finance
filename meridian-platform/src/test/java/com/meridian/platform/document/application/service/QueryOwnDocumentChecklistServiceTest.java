package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.port.out.DocumentChecklistRepository;
import com.meridian.platform.document.application.port.out.DocumentRepository;
import com.meridian.platform.document.application.port.out.LoanDocumentWorkflowPort;
import com.meridian.platform.document.domain.model.DocumentChecklist;
import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentChecklistStage;
import com.meridian.platform.document.domain.model.DocumentRequirementStatus;
import com.meridian.platform.document.domain.model.DocumentReviewDecision;
import com.meridian.platform.document.domain.model.DocumentReviewOutcome;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.document.domain.model.DocumentUploaderActorType;
import com.meridian.platform.document.domain.model.DocumentVersion;
import com.meridian.platform.document.domain.model.DocumentWaiverReasonCode;
import com.meridian.platform.document.domain.model.StoredDocument;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryOwnDocumentChecklistServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 30, 10, 0);

    @Mock LoanDocumentWorkflowPort workflows;
    @Mock DocumentChecklistRepository checklists;
    @Mock DocumentRepository documents;
    @Mock CurrentUserProvider currentUserProvider;

    private QueryOwnDocumentChecklistService service;

    @BeforeEach
    void setUp() {
        service = new QueryOwnDocumentChecklistService(
                workflows, checklists, documents, currentUserProvider
        );
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));
        when(workflows.find(APPLICATION_ID)).thenReturn(new LoanDocumentWorkflowPort
                .LoanDocumentWorkflowSnapshot(
                        APPLICATION_ID, CUSTOMER_ID, LoanApplicationStatus.DOCUMENTS_PENDING
                ));
    }

    @Test
    void projectsNoUploadAcceptedWaivedAndReplacementStatesFromCurrentEvidence() {
        UUID checklistId = UUID.randomUUID();
        DocumentChecklistItem missing = item(checklistId, DocumentType.INCOME_PROOF, null);
        UUID acceptedDecisionId = UUID.randomUUID();
        DocumentChecklistItem accepted = item(
                checklistId, DocumentType.BANK_STATEMENT, acceptedDecisionId);
        UUID waivedDecisionId = UUID.randomUUID();
        DocumentChecklistItem waived = item(
                checklistId, DocumentType.RECENT_PAYSLIP, waivedDecisionId);
        DocumentChecklistItem awaiting = item(
                checklistId, DocumentType.COLLATERAL_OWNERSHIP_EVIDENCE, null);
        UUID replacementDecisionId = UUID.randomUUID();
        DocumentChecklistItem replacement = item(
                checklistId, DocumentType.EMPLOYMENT_PROOF, replacementDecisionId);
        when(checklists.findByLoanApplicationIdAndStage(
                APPLICATION_ID, DocumentChecklistStage.SUBMISSION))
                .thenReturn(Optional.of(new DocumentChecklist(
                        checklistId, APPLICATION_ID, DocumentChecklistStage.SUBMISSION,
                        List.of(missing, accepted, waived, awaiting, replacement), NOW
                )));
        when(documents.findDocumentByChecklistItemId(missing.id())).thenReturn(Optional.empty());
        stubCurrentVersion(accepted, acceptedDecisionId, DocumentReviewOutcome.ACCEPT_DOCUMENT);
        stubCurrentVersion(waived, waivedDecisionId, DocumentReviewOutcome.WAIVE_DOCUMENT);
        stubAwaitingVersion(awaiting);
        stubCurrentVersion(replacement, replacementDecisionId,
                DocumentReviewOutcome.REQUEST_REPLACEMENT);

        var result = service.query(APPLICATION_ID);

        assertEquals(List.of(
                        "NOT_UPLOADED", "ACCEPTED", "WAIVED", "AWAITING_REVIEW",
                        "REPLACEMENT_REQUESTED"),
                result.items().stream().map(item -> item.customerStatus()).toList());
        assertEquals(false, result.uploadComplete());
        assertEquals(false, result.processingReady());
        assertEquals(null, result.items().getFirst().currentVersion());
        assertEquals("application/pdf", result.items().get(1).currentVersion().mimeType());
    }

    @Test
    void emptySalaryAdvanceChecklistIsUploadCompleteAndProcessingReady() {
        UUID checklistId = UUID.randomUUID();
        when(checklists.findByLoanApplicationIdAndStage(
                APPLICATION_ID, DocumentChecklistStage.SUBMISSION))
                .thenReturn(Optional.of(new DocumentChecklist(
                        checklistId, APPLICATION_ID, DocumentChecklistStage.SUBMISSION,
                        List.of(), NOW
                )));

        var result = service.query(APPLICATION_ID);

        assertEquals(List.of(), result.items());
        assertEquals(true, result.uploadComplete());
        assertEquals(true, result.processingReady());
    }

    @Test
    void foreignOwnershipAndMissingChecklistUseConcealedNotFound() {
        when(workflows.find(APPLICATION_ID)).thenReturn(new LoanDocumentWorkflowPort
                .LoanDocumentWorkflowSnapshot(
                        APPLICATION_ID, UUID.randomUUID(), LoanApplicationStatus.SUBMITTED
                ));
        EntityNotFoundException foreign = assertThrows(
                EntityNotFoundException.class, () -> service.query(APPLICATION_ID));

        when(workflows.find(APPLICATION_ID)).thenThrow(new EntityNotFoundException(
                "LOAN_APPLICATION_NOT_FOUND", "Loan application was not found."));
        EntityNotFoundException missing = assertThrows(
                EntityNotFoundException.class, () -> service.query(APPLICATION_ID));

        assertEquals("DOCUMENT_CHECKLIST_NOT_FOUND", foreign.getErrorCode());
        assertEquals(foreign.getErrorCode(), missing.getErrorCode());
    }

    private void stubCurrentVersion(
            DocumentChecklistItem item,
            UUID decisionId,
            DocumentReviewOutcome outcome
    ) {
        UUID versionId = stubVersion(item);
        DocumentReviewDecision decision = switch (outcome) {
            case REQUEST_REPLACEMENT -> new DocumentReviewDecision(
                    decisionId, item.id(), versionId, UUID.randomUUID(), outcome, null,
                    "DOCUMENT_REPLACEMENT_REQUIRED", "Upload a clearer copy.",
                    "restricted note", UUID.randomUUID(), NOW);
            case WAIVE_DOCUMENT -> new DocumentReviewDecision(
                    decisionId, item.id(), versionId, UUID.randomUUID(), outcome,
                    DocumentWaiverReasonCode.DOCUMENT_NOT_APPLICABLE,
                    "restricted note", UUID.randomUUID(), NOW);
            default -> new DocumentReviewDecision(
                    decisionId, item.id(), versionId, UUID.randomUUID(), outcome, null,
                    "restricted note", UUID.randomUUID(), NOW);
        };
        when(documents.findReviewDecisionById(decisionId)).thenReturn(Optional.of(decision));
    }

    private void stubAwaitingVersion(DocumentChecklistItem item) {
        stubVersion(item);
    }

    private UUID stubVersion(DocumentChecklistItem item) {
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(documents.findDocumentByChecklistItemId(item.id())).thenReturn(Optional.of(
                new StoredDocument(documentId, item.id(), versionId, NOW, NOW)
        ));
        when(documents.findVersionById(versionId)).thenReturn(Optional.of(new DocumentVersion(
                versionId, documentId, 1, UUID.randomUUID(), null, "evidence.pdf",
                "application/pdf", "application/pdf", 1024,
                "a".repeat(64), "restricted/storage/key", DocumentUploaderActorType.CUSTOMER,
                UUID.randomUUID(), NOW
        )));
        return versionId;
    }

    private static DocumentChecklistItem item(
            UUID checklistId,
            DocumentType type,
            UUID decisionId
    ) {
        return new DocumentChecklistItem(
                UUID.randomUUID(), checklistId, type, DocumentRequirementStatus.REQUIRED,
                decisionId, NOW, NOW
        );
    }

    private static AuthenticatedUser customer(UUID customerId) {
        return new AuthenticatedUser(
                UUID.randomUUID(), "customer@meridian.test", "CUSTOMER", customerId,
                Set.of("CUSTOMER"), Set.of("document:read:own")
        );
    }
}
