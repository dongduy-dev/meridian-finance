package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.UploadAssistedActionEvidenceCommand;
import com.meridian.platform.document.application.port.out.*;
import com.meridian.platform.document.domain.model.*;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
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
import static org.mockito.Mockito.*;

class AssistedActionEvidenceServiceTest {
    private static final String HASH = "b".repeat(64);
    private final AssistedActionDocumentRepository documents = mock(AssistedActionDocumentRepository.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final LoanAssistedActionAuthorizationPort authorizations = mock(LoanAssistedActionAuthorizationPort.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final BusinessAuditPublisher audits = mock(BusinessAuditPublisher.class);
    private final UUID applicationId = UUID.randomUUID();
    private final UUID offerId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private AssistedActionEvidenceService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        when(users.currentUser()).thenReturn(staff(Set.of("LOAN_OFFICER"),
                Set.of("document:upload:assisted-action")));
        when(storage.stage(any(), any(), any())).thenReturn(staged());
        when(storage.commit(any())).thenReturn(new StoredObject("assisted-actions/final/object"));
        when(documents.saveDocument(any())).thenAnswer(call -> call.getArgument(0));
        when(documents.saveVersion(any())).thenAnswer(call -> call.getArgument(0));
        service = new AssistedActionEvidenceService(
                documents, storage, authorizations, users, audits,
                Clock.fixed(Instant.parse("2026-09-22T08:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void cleanUp() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void storesImmutableOfferEvidenceAndRegistersFinalStorageRollbackCleanup() {
        when(documents.findOfferDocumentForUpdate(applicationId, offerId)).thenReturn(Optional.empty());

        var result = service.upload(command(UUID.randomUUID(), null, AssistedOfferDecision.ACCEPT));

        assertEquals(1, result.versionNumber());
        verify(authorizations).authorizeOfferEvidence(applicationId, offerId, "ACCEPT");
        verify(documents).saveVersion(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).deleteFinal("assisted-actions/final/object");
    }

    @Test
    void exactReplayReturnsExistingImmutableVersionWithoutStorageCommitOrDuplicateAudit() {
        UUID requestId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        AssistedActionDocument document = document(documentId, versionId, AssistedOfferDecision.ACCEPT);
        AssistedActionDocumentVersion version = version(versionId, documentId, requestId, null, actorId);
        when(documents.findOfferDocumentForUpdate(applicationId, offerId)).thenReturn(Optional.of(document));
        when(documents.findVersionByUploadRequestId(requestId)).thenReturn(Optional.of(version));

        assertEquals(versionId,
                service.upload(command(requestId, null, AssistedOfferDecision.ACCEPT)).documentVersionId());

        verify(storage, never()).commit(any());
        verify(documents, never()).saveVersion(any());
        verifyNoInteractions(audits);
    }

    @Test
    void reusedUploadRequestWithDifferentSemanticContentFailsClosed() {
        UUID requestId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        AssistedActionDocument document = document(documentId, null, AssistedOfferDecision.ACCEPT);
        AssistedActionDocumentVersion prior = version(
                UUID.randomUUID(), documentId, requestId, null, UUID.randomUUID());
        when(documents.findOfferDocumentForUpdate(applicationId, offerId)).thenReturn(Optional.of(document));
        when(documents.findVersionByUploadRequestId(requestId)).thenReturn(Optional.of(prior));

        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.upload(command(requestId, null, AssistedOfferDecision.ACCEPT)));

        assertEquals("IDEMPOTENCY_KEY_REUSED", error.getErrorCode());
        verify(storage, never()).commit(any());
    }

    @Test
    void staleReplacementBaselineFailsBeforeFinalStorageCommit() {
        AssistedActionDocument document = document(
                UUID.randomUUID(), UUID.randomUUID(), AssistedOfferDecision.ACCEPT);
        when(documents.findOfferDocumentForUpdate(applicationId, offerId)).thenReturn(Optional.of(document));

        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.upload(command(
                        UUID.randomUUID(), UUID.randomUUID(), AssistedOfferDecision.ACCEPT)));

        assertEquals("STALE_DOCUMENT_VERSION", error.getErrorCode());
        verify(storage, never()).commit(any());
        verify(documents, never()).saveVersion(any());
    }

    @Test
    void existingOfferEvidenceCannotBeRelabeledWithAnotherDeclaredDecision() {
        AssistedActionDocument document = document(UUID.randomUUID(), null, AssistedOfferDecision.ACCEPT);
        when(documents.findOfferDocumentForUpdate(applicationId, offerId)).thenReturn(Optional.of(document));

        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.upload(command(UUID.randomUUID(), null, AssistedOfferDecision.DECLINE)));

        assertEquals("ASSISTED_ACTION_EVIDENCE_INVALID", error.getErrorCode());
        verify(storage, never()).commit(any());
    }

    @Test
    void exactDocumentPermissionAndBusinessRoleAreBothRequired() {
        for (AuthenticatedUser invalid : new AuthenticatedUser[] {
                staff(Set.of("LOAN_OFFICER"), Set.of("document:upload:assisted-action.extra")),
                staff(Set.of("ACCOUNTING_OFFICER"), Set.of("document:upload:assisted-action")),
                new AuthenticatedUser(actorId, "customer@meridian.test", "CUSTOMER", UUID.randomUUID(),
                        Set.of("CUSTOMER"), Set.of("document:upload:assisted-action"))
        }) {
            reset(users, authorizations, documents, storage, audits);
            when(users.currentUser()).thenReturn(invalid);
            when(storage.stage(any(), any(), any())).thenReturn(staged());
            assertThrows(AuthorizationException.class,
                    () -> service.upload(command(UUID.randomUUID(), null, AssistedOfferDecision.ACCEPT)));
            verifyNoInteractions(authorizations, documents, audits);
            verify(storage, never()).commit(any());
        }
    }

    @Test
    void loanOwnedCompletedActionAuthorizationBlocksEvidenceReplacement() {
        doThrow(new BusinessStateConflictException(
                "ASSISTED_ACTION_NOT_ALLOWED", "Recorded evidence cannot be replaced."))
                .when(authorizations).authorizeOfferEvidence(applicationId, offerId, "ACCEPT");

        assertThrows(BusinessStateConflictException.class,
                () -> service.upload(command(UUID.randomUUID(), null, AssistedOfferDecision.ACCEPT)));

        verifyNoInteractions(documents, audits);
        verify(storage, never()).commit(any());
    }

    private UploadAssistedActionEvidenceCommand command(
            UUID requestId, UUID expectedVersionId, AssistedOfferDecision decision
    ) {
        return new UploadAssistedActionEvidenceCommand(
                applicationId, AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE,
                offerId, decision, null, null, requestId, expectedVersionId,
                "customer-offer-response.pdf", "application/pdf",
                new ByteArrayInputStream(new byte[] {1}));
    }

    private AssistedActionDocument document(
            UUID documentId, UUID currentVersionId, AssistedOfferDecision decision
    ) {
        return new AssistedActionDocument(
                documentId, applicationId, AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE,
                offerId, decision, null, null, currentVersionId,
                LocalDateTime.of(2026, 9, 22, 7, 0), LocalDateTime.of(2026, 9, 22, 7, 0));
    }

    private AssistedActionDocumentVersion version(
            UUID versionId, UUID documentId, UUID requestId, UUID baselineId, UUID uploaderId
    ) {
        return new AssistedActionDocumentVersion(
                versionId, documentId, 1, requestId, baselineId, "customer-offer-response.pdf",
                "application/pdf", "application/pdf", 100, HASH, "stored", uploaderId,
                LocalDateTime.of(2026, 9, 22, 8, 0));
    }

    private AuthenticatedUser staff(Set<String> roles, Set<String> permissions) {
        return new AuthenticatedUser(
                actorId, "staff@meridian.test", "STAFF", null, roles, permissions);
    }

    private static StagedDocument staged() {
        return new StagedDocument(
                UUID.randomUUID(), "customer-offer-response.pdf", "application/pdf",
                "application/pdf", 100, HASH);
    }
}
