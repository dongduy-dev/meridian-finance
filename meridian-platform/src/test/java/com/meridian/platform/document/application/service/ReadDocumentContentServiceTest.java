package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.port.out.*;
import com.meridian.platform.document.domain.model.*;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.shared.application.security.*;
import com.meridian.platform.shared.domain.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadDocumentContentServiceTest {
    final LoanDocumentWorkflowPort workflows = mock(LoanDocumentWorkflowPort.class);
    final DocumentChecklistRepository checklists = mock(DocumentChecklistRepository.class);
    final DocumentRepository documents = mock(DocumentRepository.class);
    final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    final CurrentUserProvider users = mock(CurrentUserProvider.class);
    final ReadDocumentContentService service = new ReadDocumentContentService(workflows, checklists, documents, storage, users);

    @ParameterizedTest @ValueSource(strings = {"document:review", "approval:decide"})
    void staffReadsExactHistoricalVersionAndRejectsForeignVersion(String permission) throws Exception {
        UUID applicationId = UUID.randomUUID(), checklistId = UUID.randomUUID(), itemId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID(), versionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 10, 3, 9, 0);
        when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "staff@meridian.test", "STAFF", null, Set.of(), Set.of(permission)));
        when(workflows.find(applicationId)).thenReturn(new LoanDocumentWorkflowPort.LoanDocumentWorkflowSnapshot(applicationId, UUID.randomUUID(), LoanApplicationStatus.SUBMITTED));
        var item = new DocumentChecklistItem(itemId, checklistId, DocumentType.BANK_STATEMENT, DocumentRequirementStatus.REQUIRED, null, now, now);
        when(checklists.findByLoanApplicationIdAndStage(applicationId, DocumentChecklistStage.SUBMISSION))
                .thenReturn(Optional.of(new DocumentChecklist(checklistId, applicationId, DocumentChecklistStage.SUBMISSION, List.of(item), now)));
        when(checklists.findItemById(itemId)).thenReturn(Optional.of(item));
        when(documents.findDocumentByChecklistItemId(itemId)).thenReturn(Optional.of(new StoredDocument(documentId, itemId, UUID.randomUUID(), now, now)));
        var version = new DocumentVersion(versionId, documentId, 1, UUID.randomUUID(), null, "old.pdf", "application/pdf", "application/pdf", 3,
                "a".repeat(64), "private/old", DocumentUploaderActorType.CUSTOMER, UUID.randomUUID(), now);
        when(documents.findVersionById(versionId)).thenReturn(Optional.of(version));
        when(storage.open("private/old")).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        assertArrayEquals(new byte[]{1, 2, 3}, service.readAsStaff(applicationId, itemId, versionId).content().readAllBytes());
        when(documents.findDocumentByChecklistItemId(itemId)).thenReturn(Optional.of(new StoredDocument(UUID.randomUUID(), itemId, UUID.randomUUID(), now, now)));
        assertThrows(EntityNotFoundException.class, () -> service.readAsStaff(applicationId, itemId, versionId));
        verify(storage, times(1)).open(anyString());
        verify(documents, never()).saveVersion(any());
    }

    @Test void staffRouteRejectsCustomerContextBeforeLoadingWorkflow() {
        for (String type : List.of("CUSTOMER", "STAFF")) {
            when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "customer@meridian.test", type,
                    UUID.randomUUID(), Set.of(), Set.of("document:review", "approval:decide", "document:read:own")));
            assertThrows(AuthorizationException.class, () -> service.readAsStaff(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        }
        verifyNoInteractions(workflows, checklists, documents, storage);
    }

    @Test void approverReadAuthorityDoesNotExpandTheExistingCustomerContentPath() {
        UUID app = UUID.randomUUID();
        when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "staff@meridian.test", "STAFF", null,
                Set.of(), Set.of("approval:decide", "document:read:own")));
        when(workflows.find(app)).thenReturn(new LoanDocumentWorkflowPort.LoanDocumentWorkflowSnapshot(app, UUID.randomUUID(), LoanApplicationStatus.SUBMITTED));
        assertThrows(AuthorizationException.class, () -> service.read(app, UUID.randomUUID(), UUID.randomUUID()));
        verifyNoInteractions(checklists, documents, storage);
    }
}
