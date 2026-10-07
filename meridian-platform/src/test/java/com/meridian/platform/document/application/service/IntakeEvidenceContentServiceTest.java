package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.application.port.out.IntakeDocumentRepository;
import com.meridian.platform.document.application.port.out.LoanAssistedOriginationPort;
import com.meridian.platform.document.domain.model.IntakeDocument;
import com.meridian.platform.document.domain.model.IntakeDocumentVersion;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntakeEvidenceContentServiceTest {
    private final IntakeDocumentRepository documents = mock(IntakeDocumentRepository.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final LoanAssistedOriginationPort cases = mock(LoanAssistedOriginationPort.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final BusinessAuditPublisher audits = mock(BusinessAuditPublisher.class);
    private final UUID caseId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID currentId = UUID.randomUUID();
    private final IntakeEvidenceType type = IntakeEvidenceType.UCL_PAPER_APPLICATION;
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 7, 10, 0);
    private final IntakeEvidenceService service = new IntakeEvidenceService(
            documents, storage, cases, users, audits, Clock.systemUTC());

    @BeforeEach
    void setUp() {
        actor("STAFF", null, Set.of("document:upload:intake"));
        when(cases.authorizeRead(caseId)).thenReturn(
                new LoanAssistedOriginationPort.AuthorizedIntake(caseId, "UNSECURED_CONSUMER_LOAN"));
        when(documents.findByCaseAndType(caseId, type)).thenReturn(Optional.of(
                new IntakeDocument(documentId, caseId, type, currentId, now, now)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void readsExactCurrentOrHistoricalVersionWithoutMutation(boolean historical) throws Exception {
        UUID selectedId = historical ? UUID.randomUUID() : currentId;
        String filename = historical ? "paper-v1.png" : "paper-v2.pdf";
        String mime = historical ? "image/png" : "application/pdf";
        byte[] bytes = historical ? new byte[]{1, 2, 3} : new byte[]{4, 5};
        when(documents.findVersionById(selectedId)).thenReturn(Optional.of(new IntakeDocumentVersion(
                selectedId, documentId, historical ? 1 : 2, UUID.randomUUID(), null,
                filename, mime, mime, bytes.length, "a".repeat(64), "private/object", UUID.randomUUID(), now)));
        when(storage.open("private/object")).thenReturn(new ByteArrayInputStream(bytes));

        var result = service.readContent(caseId, type, selectedId);

        assertEquals(filename, result.originalFilename());
        assertEquals(mime, result.detectedMimeType());
        assertEquals(bytes.length, result.byteSize());
        try (var content = result.content()) { assertArrayEquals(bytes, content.readAllBytes()); }
        var order = inOrder(cases, documents, storage);
        order.verify(cases).authorizeRead(caseId);
        order.verify(documents).findByCaseAndType(caseId, type);
        order.verify(documents).findVersionById(selectedId);
        order.verify(storage).open("private/object");
        verify(documents, never()).saveDocument(any());
        verify(documents, never()).saveVersion(any());
        verify(cases, never()).authorizeMutation(any());
        verifyNoInteractions(audits);
    }

    @ParameterizedTest
    @ValueSource(strings = {"loan:read", "document:review", "document:upload:staff"})
    void otherStaffAuthoritiesCannotReadIntakeBytes(String permission) {
        actor("STAFF", null, Set.of(permission));
        assertThrows(AuthorizationException.class, () -> service.readContent(caseId, type, currentId));
        verifyNoInteractions(cases, documents, storage);
    }

    @Test
    void customerOrStaffWithCustomerAssociationIsDeniedEvenWithIntakePermission() {
        for (String userType : Set.of("CUSTOMER", "STAFF")) {
            actor(userType, UUID.randomUUID(), Set.of("document:upload:intake"));
            assertThrows(AuthorizationException.class, () -> service.readContent(caseId, type, currentId));
        }
        verifyNoInteractions(cases, documents, storage);
    }

    @Test
    void loanMustAuthorizeTheCaseBeforeDocumentOrStorageAccess() {
        when(cases.authorizeRead(caseId)).thenThrow(new AuthorizationException(
                "ASSISTED_ORIGINATION_ACCESS_DENIED", "Staff-assisted origination access is denied."));
        assertThrows(AuthorizationException.class, () -> service.readContent(caseId, type, currentId));
        verifyNoInteractions(documents, storage);
    }

    @Test
    void missingCaseFailsBeforeDocumentOrStorageAccess() {
        when(cases.authorizeRead(caseId)).thenThrow(new EntityNotFoundException(
                "ASSISTED_ORIGINATION_CASE_NOT_FOUND", "Assisted origination case was not found."));
        assertThrows(EntityNotFoundException.class, () -> service.readContent(caseId, type, currentId));
        verifyNoInteractions(documents, storage);
    }

    @Test
    void productIncompatibleEvidenceFailsBeforeDocumentOrStorageAccess() {
        assertThrows(BusinessRuleViolationException.class, () -> service.readContent(
                caseId, IntakeEvidenceType.COLLATERAL_PAPER_APPLICATION, currentId));
        verifyNoInteractions(documents, storage);
    }

    @Test
    void wrongCaseOrEvidenceTypeCannotResolveTheLogicalDocument() {
        for (IntakeDocument wrong : new IntakeDocument[]{
                new IntakeDocument(documentId, UUID.randomUUID(), type, currentId, now, now),
                new IntakeDocument(documentId, caseId, IntakeEvidenceType.CUSTOMER_IDENTITY, currentId, now, now)}) {
            when(documents.findByCaseAndType(caseId, type)).thenReturn(Optional.of(wrong));
            assertThrows(EntityNotFoundException.class, () -> service.readContent(caseId, type, currentId));
        }
        when(documents.findByCaseAndType(caseId, type)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.readContent(caseId, type, currentId));
        verify(documents, never()).findVersionById(any());
        verifyNoInteractions(storage);
    }

    @Test
    void unknownOrForeignVersionFailsWithoutOpeningStorage() {
        when(documents.findVersionById(currentId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.readContent(caseId, type, currentId));
        when(documents.findVersionById(currentId)).thenReturn(Optional.of(version(UUID.randomUUID())));
        var error = assertThrows(EntityNotFoundException.class, () -> service.readContent(caseId, type, currentId));
        assertEquals("Intake evidence version was not found.", error.getMessage());
        verifyNoInteractions(storage);
    }

    @Test
    void storageAvailabilityFailureIsPreservedWithoutMutationOrFallback() {
        when(documents.findVersionById(currentId)).thenReturn(Optional.of(version(documentId)));
        when(storage.open("private/object")).thenThrow(new ServiceUnavailableException(
                "DOCUMENT_STORAGE_UNAVAILABLE", "Document storage is temporarily unavailable."));
        var error = assertThrows(ServiceUnavailableException.class, () -> service.readContent(caseId, type, currentId));
        assertFalse(error.getMessage().contains("private/object"));
        verify(documents, never()).saveDocument(any());
        verify(documents, never()).saveVersion(any());
        verifyNoInteractions(audits);
    }

    private IntakeDocumentVersion version(UUID owner) {
        return new IntakeDocumentVersion(currentId, owner, 2, UUID.randomUUID(), null, "paper.pdf",
                "application/pdf", "application/pdf", 2, "a".repeat(64), "private/object", UUID.randomUUID(), now);
    }

    private void actor(String userType, UUID customerId, Set<String> permissions) {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "staff@meridian.local",
                userType, customerId, Set.of(), permissions));
    }
}
