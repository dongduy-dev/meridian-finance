package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.UploadDocumentCommand;
import com.meridian.platform.document.application.port.out.DocumentChecklistRepository;
import com.meridian.platform.document.application.port.out.DocumentRepository;
import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.application.port.out.DocumentWorkflowEventPublisher;
import com.meridian.platform.document.application.port.out.LoanDocumentCorrectionPort;
import com.meridian.platform.document.application.port.out.LoanDocumentWorkflowPort;
import com.meridian.platform.document.application.port.out.StagedDocument;
import com.meridian.platform.document.application.port.out.StoredObject;
import com.meridian.platform.document.domain.model.DocumentChecklist;
import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentChecklistReadiness;
import com.meridian.platform.document.domain.model.DocumentChecklistStage;
import com.meridian.platform.document.domain.model.DocumentRequirementStatus;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.document.domain.model.DocumentUploaderActorType;
import com.meridian.platform.document.domain.model.DocumentVersion;
import com.meridian.platform.document.domain.model.StoredDocument;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionalDocumentUploadServiceTest {

    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CHECKLIST_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 8, 0);
    private static final StagedDocument STAGED = new StagedDocument(
            UUID.randomUUID(), "evidence.pdf", "application/pdf", "application/pdf", 128, "a".repeat(64));

    @Mock LoanDocumentWorkflowPort workflows;
    @Mock DocumentChecklistRepository checklists;
    @Mock DocumentRepository documents;
    @Mock DocumentStoragePort storage;
    @Mock LoanDocumentCorrectionPort corrections;
    @Mock DocumentWorkflowEventPublisher events;
    @Mock BusinessAuditPublisher audits;
    @Mock CurrentUserProvider currentUsers;

    private TransactionalDocumentUploadService service;

    @BeforeEach
    void setUp() {
        service = new TransactionalDocumentUploadService(
                workflows, checklists, documents, storage, corrections, events, audits, currentUsers,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void customerDigitalInitialUploadRetainsExistingBehavior() {
        prepareSuccessfulUpload(customer(), OriginationChannel.CUSTOMER_DIGITAL,
                LoanApplicationStatus.DOCUMENTS_PENDING, false);

        service.store(command(DocumentUploaderActorType.CUSTOMER, null), STAGED);

        verify(corrections, never()).authorizeCustomerUpload(any(), any(), any());
        verify(documents).saveVersion(any());
    }

    @Test
    void assistedInitialUploadWithNarrowPermissionDoesNotRequireCorrectionTask() {
        prepareSuccessfulUpload(staff("document:upload:assisted"), OriginationChannel.STAFF_ASSISTED,
                LoanApplicationStatus.DOCUMENTS_PENDING, false);

        service.store(command(DocumentUploaderActorType.STAFF, null), STAGED);

        verify(corrections, never()).authorizeStaffUpload(any(), any(), any());
        verify(documents).saveVersion(any());
    }

    @Test
    void correctionPermissionAloneCannotAuthorizeAssistedInitialUpload() {
        assertDenied(staff("document:upload:staff"), OriginationChannel.STAFF_ASSISTED,
                LoanApplicationStatus.DOCUMENTS_PENDING, DocumentUploaderActorType.STAFF);
    }

    @Test
    void intakePermissionCannotAuthorizeApplicationChecklistUpload() {
        assertDenied(staff("document:upload:intake"), OriginationChannel.STAFF_ASSISTED,
                LoanApplicationStatus.DOCUMENTS_PENDING, DocumentUploaderActorType.STAFF);
    }

    @Test
    void assistedPermissionCannotAuthorizeDigitalOrCorrectionUploads() {
        assertDenied(staff("document:upload:assisted"), OriginationChannel.CUSTOMER_DIGITAL,
                LoanApplicationStatus.DOCUMENTS_PENDING, DocumentUploaderActorType.STAFF);
        assertDenied(staff("document:upload:assisted"), OriginationChannel.STAFF_ASSISTED,
                LoanApplicationStatus.SUBMITTED, DocumentUploaderActorType.STAFF);
    }

    @Test
    void customerCannotUploadToStaffAssistedApplication() {
        assertDenied(customer(), OriginationChannel.STAFF_ASSISTED,
                LoanApplicationStatus.DOCUMENTS_PENDING, DocumentUploaderActorType.CUSTOMER);
    }

    @Test
    void exactUploadRequestReplayIsIdempotent() {
        UUID requestId = UUID.randomUUID();
        prepareWorkflow(customer(), OriginationChannel.CUSTOMER_DIGITAL, LoanApplicationStatus.DOCUMENTS_PENDING);
        when(checklists.findByLoanApplicationIdAndStage(APPLICATION_ID, DocumentChecklistStage.SUBMISSION))
                .thenReturn(Optional.of(checklist()));
        when(checklists.findItemByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(item()));
        when(documents.findDocumentByChecklistItemIdForUpdate(ITEM_ID))
                .thenReturn(Optional.of(storedDocument(null)));
        when(documents.findVersionByUploadRequestId(requestId)).thenReturn(Optional.of(version(requestId, null)));

        service.store(command(DocumentUploaderActorType.CUSTOMER, requestId, null), STAGED);

        verify(storage, never()).commit(any());
        verify(documents, never()).saveVersion(any());
    }

    @Test
    void reusedUploadRequestForDifferentContentIsRejected() {
        UUID requestId = UUID.randomUUID();
        prepareWorkflow(customer(), OriginationChannel.CUSTOMER_DIGITAL, LoanApplicationStatus.DOCUMENTS_PENDING);
        when(checklists.findByLoanApplicationIdAndStage(APPLICATION_ID, DocumentChecklistStage.SUBMISSION))
                .thenReturn(Optional.of(checklist()));
        when(checklists.findItemByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(item()));
        when(documents.findDocumentByChecklistItemIdForUpdate(ITEM_ID))
                .thenReturn(Optional.of(storedDocument(null)));
        when(documents.findVersionByUploadRequestId(requestId)).thenReturn(Optional.of(version(requestId, null)));
        StagedDocument changed = new StagedDocument(UUID.randomUUID(), "changed.pdf", "application/pdf",
                "application/pdf", 128, "b".repeat(64));

        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.store(command(DocumentUploaderActorType.CUSTOMER, requestId, null), changed));

        assertEquals("IDEMPOTENCY_KEY_REUSED", error.getErrorCode());
    }

    @Test
    void staleExpectedVersionRemainsRejected() {
        UUID currentVersionId = UUID.randomUUID();
        prepareWorkflow(staff("document:upload:assisted"), OriginationChannel.STAFF_ASSISTED,
                LoanApplicationStatus.DOCUMENTS_PENDING);
        when(checklists.findByLoanApplicationIdAndStage(APPLICATION_ID, DocumentChecklistStage.SUBMISSION))
                .thenReturn(Optional.of(checklist()));
        when(checklists.findItemByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(item()));
        when(documents.findDocumentByChecklistItemIdForUpdate(ITEM_ID))
                .thenReturn(Optional.of(storedDocument(currentVersionId)));
        when(documents.findVersionByUploadRequestId(any())).thenReturn(Optional.empty());

        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class,
                () -> service.store(command(DocumentUploaderActorType.STAFF, null), STAGED));

        assertEquals("STALE_DOCUMENT_VERSION", error.getErrorCode());
    }

    @Test
    void rollbackDeletesCommittedStorageObject() {
        prepareSuccessfulUpload(staff("document:upload:assisted"), OriginationChannel.STAFF_ASSISTED,
                LoanApplicationStatus.DOCUMENTS_PENDING, false);

        service.store(command(DocumentUploaderActorType.STAFF, null), STAGED);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(storage).deleteFinal("documents/evidence.pdf");
    }

    @Test
    void finalRequiredUploadPublishesEstablishedCompletionEvent() {
        prepareSuccessfulUpload(staff("document:upload:assisted"), OriginationChannel.STAFF_ASSISTED,
                LoanApplicationStatus.DOCUMENTS_PENDING, true);

        service.store(command(DocumentUploaderActorType.STAFF, null), STAGED);

        verify(events).publish(any());
    }

    private void assertDenied(
            AuthenticatedUser actor,
            OriginationChannel channel,
            LoanApplicationStatus status,
            DocumentUploaderActorType actorType
    ) {
        prepareWorkflow(actor, channel, status);
        AuthorizationException error = assertThrows(AuthorizationException.class,
                () -> service.store(command(actorType, null), STAGED));
        assertEquals(channel == OriginationChannel.STAFF_ASSISTED && actorType == DocumentUploaderActorType.CUSTOMER
                ? "CUSTOMER_DIRECT_ACTION_NOT_ALLOWED" : "DOCUMENT_ACCESS_DENIED", error.getErrorCode());
    }

    private void prepareWorkflow(AuthenticatedUser actor, OriginationChannel channel, LoanApplicationStatus status) {
        when(currentUsers.currentUser()).thenReturn(actor);
        when(workflows.lock(APPLICATION_ID)).thenReturn(
                new LoanDocumentWorkflowPort.LoanDocumentWorkflowSnapshot(APPLICATION_ID, CUSTOMER_ID, channel, status));
    }

    private void prepareSuccessfulUpload(
            AuthenticatedUser actor,
            OriginationChannel channel,
            LoanApplicationStatus status,
            boolean uploadComplete
    ) {
        prepareWorkflow(actor, channel, status);
        when(checklists.findByLoanApplicationIdAndStage(APPLICATION_ID, DocumentChecklistStage.SUBMISSION))
                .thenReturn(Optional.of(checklist()));
        when(checklists.findItemByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(item()));
        when(documents.findDocumentByChecklistItemIdForUpdate(ITEM_ID)).thenReturn(Optional.empty());
        when(documents.saveDocument(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documents.findVersionByUploadRequestId(any())).thenReturn(Optional.empty());
        when(storage.commit(STAGED)).thenReturn(new StoredObject("documents/evidence.pdf"));
        when(documents.saveVersion(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(checklists.findReadiness(APPLICATION_ID, DocumentChecklistStage.SUBMISSION))
                .thenReturn(new DocumentChecklistReadiness(uploadComplete, false));
    }

    private UploadDocumentCommand command(DocumentUploaderActorType actorType, UUID requestId) {
        return command(actorType, requestId == null ? UUID.randomUUID() : requestId, null);
    }

    private UploadDocumentCommand command(
            DocumentUploaderActorType actorType,
            UUID requestId,
            UUID expectedVersionId
    ) {
        return new UploadDocumentCommand(
                APPLICATION_ID, ITEM_ID, requestId, expectedVersionId, "evidence.pdf", "application/pdf",
                InputStream.nullInputStream(), actorType, USER_ID,
                actorType == DocumentUploaderActorType.CUSTOMER ? CUSTOMER_ID : null);
    }

    private DocumentChecklist checklist() {
        return new DocumentChecklist(CHECKLIST_ID, APPLICATION_ID, DocumentChecklistStage.SUBMISSION,
                List.of(item()), NOW);
    }

    private DocumentChecklistItem item() {
        return new DocumentChecklistItem(ITEM_ID, CHECKLIST_ID, DocumentType.INCOME_PROOF,
                DocumentRequirementStatus.REQUIRED, null, NOW, NOW);
    }

    private StoredDocument storedDocument(UUID currentVersionId) {
        return new StoredDocument(DOCUMENT_ID, ITEM_ID, currentVersionId, NOW, NOW);
    }

    private DocumentVersion version(UUID requestId, UUID baseline) {
        return new DocumentVersion(UUID.randomUUID(), DOCUMENT_ID, 1, requestId, baseline, "evidence.pdf",
                "application/pdf", "application/pdf", 128, "a".repeat(64), "documents/evidence.pdf",
                DocumentUploaderActorType.CUSTOMER, USER_ID, NOW);
    }

    private AuthenticatedUser customer() {
        return new AuthenticatedUser(USER_ID, "customer@meridian.test", "CUSTOMER", CUSTOMER_ID,
                Set.of("CUSTOMER"), Set.of("document:upload:own"));
    }

    private AuthenticatedUser staff(String permission) {
        return new AuthenticatedUser(USER_ID, "staff@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of(permission));
    }
}
