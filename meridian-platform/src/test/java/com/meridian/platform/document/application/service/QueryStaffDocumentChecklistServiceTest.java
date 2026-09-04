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
import com.meridian.platform.document.domain.model.StoredDocument;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
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
class QueryStaffDocumentChecklistServiceTest {
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID CHECKLIST_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID VERSION_ONE_ID = UUID.randomUUID();
    private static final UUID VERSION_TWO_ID = UUID.randomUUID();
    private static final UUID DECISION_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 2, 10, 0);

    @Mock LoanDocumentWorkflowPort workflows;
    @Mock DocumentChecklistRepository checklists;
    @Mock DocumentRepository documents;
    @Mock CurrentUserProvider currentUserProvider;
    private QueryStaffDocumentChecklistService service;

    @BeforeEach
    void setUp() {
        service = new QueryStaffDocumentChecklistService(
                workflows, checklists, documents, currentUserProvider);
    }

    @Test
    void returnsSafeDeterministicVersionAndReviewHistory() {
        when(currentUserProvider.currentUser()).thenReturn(staff("document:review"));
        when(workflows.find(APPLICATION_ID)).thenReturn(new LoanDocumentWorkflowPort
                .LoanDocumentWorkflowSnapshot(APPLICATION_ID, UUID.randomUUID(),
                LoanApplicationStatus.SUBMITTED));
        DocumentChecklistItem item = new DocumentChecklistItem(
                ITEM_ID, CHECKLIST_ID, DocumentType.BANK_STATEMENT,
                DocumentRequirementStatus.REQUIRED, DECISION_ID, NOW, NOW);
        when(checklists.findByLoanApplicationIdAndStage(
                APPLICATION_ID, DocumentChecklistStage.SUBMISSION)).thenReturn(Optional.of(
                new DocumentChecklist(CHECKLIST_ID, APPLICATION_ID,
                        DocumentChecklistStage.SUBMISSION, List.of(item), NOW)));
        when(documents.findDocumentByChecklistItemId(ITEM_ID)).thenReturn(Optional.of(
                new StoredDocument(DOCUMENT_ID, ITEM_ID, VERSION_TWO_ID, NOW, NOW)));
        DocumentVersion versionOne = version(VERSION_ONE_ID, 1, NOW.minusHours(2));
        DocumentVersion versionTwo = version(VERSION_TWO_ID, 2, NOW.minusHours(1));
        when(documents.findVersionsByDocumentId(DOCUMENT_ID))
                .thenReturn(List.of(versionOne, versionTwo));
        DocumentReviewDecision decision = new DocumentReviewDecision(
                DECISION_ID, ITEM_ID, VERSION_TWO_ID, UUID.randomUUID(),
                DocumentReviewOutcome.ACCEPT_DOCUMENT, null, null,
                UUID.randomUUID(), NOW);
        when(documents.findReviewDecisionsByChecklistItemId(ITEM_ID))
                .thenReturn(List.of(decision));

        var result = service.query(APPLICATION_ID);

        assertEquals("SUBMITTED", result.applicationStatus());
        assertEquals(List.of(1, 2), result.items().getFirst().versionHistory().stream()
                .map(version -> version.versionNumber()).toList());
        assertEquals("ACCEPTED", result.items().getFirst().evidenceStatus());
        assertEquals(VERSION_TWO_ID,
                result.items().getFirst().reviewHistory().getFirst().documentVersionId());
    }

    @Test
    void deniesUnrelatedPermissionAndFailsClosedForMissingCurrentVersion() {
        when(currentUserProvider.currentUser()).thenReturn(staff("document:read:own"));
        AuthorizationException denied = assertThrows(
                AuthorizationException.class, () -> service.query(APPLICATION_ID));
        assertEquals("DOCUMENT_ACCESS_DENIED", denied.getErrorCode());

        when(currentUserProvider.currentUser()).thenReturn(staff("document:review"));
        when(workflows.find(APPLICATION_ID)).thenReturn(new LoanDocumentWorkflowPort
                .LoanDocumentWorkflowSnapshot(APPLICATION_ID, UUID.randomUUID(),
                LoanApplicationStatus.SUBMITTED));
        DocumentChecklistItem item = new DocumentChecklistItem(
                ITEM_ID, CHECKLIST_ID, DocumentType.BANK_STATEMENT,
                DocumentRequirementStatus.REQUIRED, null, NOW, NOW);
        when(checklists.findByLoanApplicationIdAndStage(
                APPLICATION_ID, DocumentChecklistStage.SUBMISSION)).thenReturn(Optional.of(
                new DocumentChecklist(CHECKLIST_ID, APPLICATION_ID,
                        DocumentChecklistStage.SUBMISSION, List.of(item), NOW)));
        when(documents.findDocumentByChecklistItemId(ITEM_ID)).thenReturn(Optional.of(
                new StoredDocument(DOCUMENT_ID, ITEM_ID, VERSION_TWO_ID, NOW, NOW)));
        when(documents.findVersionsByDocumentId(DOCUMENT_ID)).thenReturn(List.of());

        BusinessStateConflictException conflict = assertThrows(
                BusinessStateConflictException.class, () -> service.query(APPLICATION_ID));
        assertEquals("SYSTEM_STATE_CONFLICT", conflict.getErrorCode());
    }

    @Test
    void concealsMissingLoanAsMissingDocumentChecklist() {
        when(currentUserProvider.currentUser()).thenReturn(staff("document:review"));
        when(workflows.find(APPLICATION_ID)).thenThrow(new EntityNotFoundException(
                "LOAN_APPLICATION_NOT_FOUND", "Loan Application was not found."));

        EntityNotFoundException missing = assertThrows(
                EntityNotFoundException.class, () -> service.query(APPLICATION_ID));

        assertEquals("DOCUMENT_CHECKLIST_NOT_FOUND", missing.getErrorCode());
    }

    private static DocumentVersion version(UUID id, int number, LocalDateTime uploadedAt) {
        return new DocumentVersion(
                id, DOCUMENT_ID, number, UUID.randomUUID(), null, "evidence.pdf",
                "application/pdf", "application/pdf", 1024, "a".repeat(64),
                "restricted/storage/key-" + number, DocumentUploaderActorType.CUSTOMER,
                UUID.randomUUID(), uploadedAt);
    }

    private static AuthenticatedUser staff(String permission) {
        return new AuthenticatedUser(
                UUID.randomUUID(), "staff@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of(permission));
    }
}
