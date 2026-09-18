package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.UploadIntakeEvidenceCommand;
import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.application.port.out.IntakeDocumentRepository;
import com.meridian.platform.document.application.port.out.LoanAssistedOriginationPort;
import com.meridian.platform.document.application.port.out.StagedDocument;
import com.meridian.platform.document.application.port.out.StoredObject;
import com.meridian.platform.document.domain.model.IntakeDocument;
import com.meridian.platform.document.domain.model.IntakeDocumentVersion;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeEvidenceServiceTest {
    private static final String HASH = "a".repeat(64);
    private final IntakeDocumentRepository documents = mock(IntakeDocumentRepository.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final LoanAssistedOriginationPort cases = mock(LoanAssistedOriginationPort.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final BusinessAuditPublisher audits = mock(BusinessAuditPublisher.class);
    private final UUID caseId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private IntakeEvidenceService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        when(users.currentUser()).thenReturn(new AuthenticatedUser(
                userId, "loan.officer@meridian.local", "STAFF", null,
                Set.of(), Set.of("document:upload:intake")));
        when(cases.authorizeMutation(caseId)).thenReturn(
                new LoanAssistedOriginationPort.AuthorizedIntake(caseId, "UNSECURED_CONSUMER_LOAN"));
        when(storage.stage(any(), any(), any())).thenReturn(staged());
        when(storage.commit(any())).thenReturn(new StoredObject("intake/final/object"));
        when(documents.saveDocument(any())).thenAnswer(call -> call.getArgument(0));
        when(documents.saveVersion(any())).thenAnswer(call -> call.getArgument(0));
        service = new IntakeEvidenceService(documents, storage, cases, users, audits,
                Clock.fixed(Instant.parse("2026-09-17T08:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void cleanUp() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void storesImmutableVersionAndRegistersRollbackCleanup() {
        when(documents.findByCaseAndTypeForUpdate(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION))
                .thenReturn(Optional.empty());

        var result = service.upload(command(UUID.randomUUID(), null));

        assertEquals(1, result.versionNumber());
        verify(documents).saveVersion(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).deleteFinal("intake/final/object");
    }

    @Test
    void exactReplayReturnsExistingVersionWithoutStorageCommit() {
        UUID requestId = UUID.randomUUID(); UUID documentId = UUID.randomUUID(); UUID versionId = UUID.randomUUID();
        IntakeDocument document = new IntakeDocument(documentId, caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION,
                versionId, LocalDateTime.now(), LocalDateTime.now());
        IntakeDocumentVersion version = new IntakeDocumentVersion(
                versionId, documentId, 1, requestId, null, "application.pdf", "application/pdf",
                "application/pdf", 100, HASH, "stored", userId, LocalDateTime.now());
        when(documents.findByCaseAndTypeForUpdate(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION))
                .thenReturn(Optional.of(document));
        when(documents.findVersionByUploadRequestId(requestId)).thenReturn(Optional.of(version));

        assertEquals(versionId, service.upload(command(requestId, null)).intakeDocumentVersionId());
        verify(storage, never()).commit(any());
    }

    @Test
    void staleExpectedVersionRejectsBeforeStorageCommit() {
        IntakeDocument document = new IntakeDocument(UUID.randomUUID(), caseId,
                IntakeEvidenceType.UCL_PAPER_APPLICATION, UUID.randomUUID(),
                LocalDateTime.now(), LocalDateTime.now());
        when(documents.findByCaseAndTypeForUpdate(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION))
                .thenReturn(Optional.of(document));

        assertThrows(BusinessStateConflictException.class,
                () -> service.upload(command(UUID.randomUUID(), UUID.randomUUID())));
        verify(storage, never()).commit(any());
    }

    @Test
    void conflictingUploadRequestReuseIsRejected() {
        UUID requestId = UUID.randomUUID(); UUID documentId = UUID.randomUUID();
        IntakeDocument document = new IntakeDocument(documentId, caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION,
                null, LocalDateTime.now(), LocalDateTime.now());
        IntakeDocumentVersion prior = new IntakeDocumentVersion(
                UUID.randomUUID(), documentId, 1, requestId, null, "different.pdf", "application/pdf",
                "application/pdf", 100, HASH, "stored", userId, LocalDateTime.now());
        when(documents.findByCaseAndTypeForUpdate(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION))
                .thenReturn(Optional.of(document));
        when(documents.findVersionByUploadRequestId(requestId)).thenReturn(Optional.of(prior));

        assertThrows(BusinessStateConflictException.class, () -> service.upload(command(requestId, null)));
        verify(storage, never()).commit(any());
    }

    @Test
    void productMismatchedPaperFormIsRejected() {
        UploadIntakeEvidenceCommand mismatch = new UploadIntakeEvidenceCommand(
                caseId, IntakeEvidenceType.COLLATERAL_PAPER_APPLICATION, UUID.randomUUID(), null,
                "application.pdf", "application/pdf", new ByteArrayInputStream(new byte[] {1}));

        assertThrows(BusinessRuleViolationException.class, () -> service.upload(mismatch));
        verify(documents, never()).saveVersion(any());
        verify(storage, never()).commit(any());
    }

    @Test
    void correctionUploadPermissionAloneDoesNotAuthorizeIntakeEvidence() {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(
                userId, "loan.officer@meridian.local", "STAFF", null,
                Set.of(), Set.of("document:upload:staff")));

        assertThrows(AuthorizationException.class, () -> service.upload(command(UUID.randomUUID(), null)));
        verify(cases, never()).authorizeMutation(any());
        verify(storage, never()).commit(any());
    }

    @Test
    void abandonedLoanOwnedIntakeAuthorizationBlocksPersistence() {
        when(cases.authorizeMutation(caseId)).thenThrow(new BusinessStateConflictException(
                "ASSISTED_ORIGINATION_CASE_NOT_OPEN", "Assisted origination case is no longer open."));

        assertThrows(BusinessStateConflictException.class,
                () -> service.upload(command(UUID.randomUUID(), null)));
        verify(documents, never()).saveDocument(any());
        verify(documents, never()).saveVersion(any());
        verify(storage, never()).commit(any());
    }

    private UploadIntakeEvidenceCommand command(UUID requestId, UUID expectedVersion) {
        return new UploadIntakeEvidenceCommand(
                caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, requestId, expectedVersion,
                "application.pdf", "application/pdf", new ByteArrayInputStream(new byte[] {1}));
    }

    private static StagedDocument staged() {
        return new StagedDocument(UUID.randomUUID(), "application.pdf", "application/pdf",
                "application/pdf", 100, HASH);
    }
}
