package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.port.out.IntakeDocumentRepository;
import com.meridian.platform.document.application.port.out.LoanAssistedOriginationPort;
import com.meridian.platform.document.application.port.out.OcrJobRepository;
import com.meridian.platform.document.domain.model.IntakeDocument;
import com.meridian.platform.document.domain.model.IntakeDocumentVersion;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.document.domain.model.OcrJob;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeOcrServiceTest {
    private static final String HASH = "a".repeat(64);
    private final IntakeDocumentRepository documents = mock(IntakeDocumentRepository.class);
    private final OcrJobRepository jobs = mock(OcrJobRepository.class);
    private final LoanAssistedOriginationPort cases = mock(LoanAssistedOriginationPort.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final BusinessAuditPublisher audits = mock(BusinessAuditPublisher.class);
    private final UUID caseId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private IntakeOcrService service;

    @BeforeEach
    void setUp() {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(
                actorId, "loan.officer@meridian.local", "STAFF", null,
                Set.of(), Set.of("document:upload:intake")));
        when(cases.authorizeMutation(caseId)).thenReturn(
                new LoanAssistedOriginationPort.AuthorizedIntake(caseId, "UNSECURED_CONSUMER_LOAN"));
        when(cases.authorizeRead(caseId)).thenReturn(
                new LoanAssistedOriginationPort.AuthorizedIntake(caseId, "UNSECURED_CONSUMER_LOAN"));
        when(documents.findByCaseAndTypeForUpdate(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION))
                .thenReturn(Optional.of(document(versionId)));
        when(documents.findByCaseAndType(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION))
                .thenReturn(Optional.of(document(versionId)));
        when(documents.findVersionById(versionId)).thenReturn(Optional.of(version(versionId, documentId)));
        when(jobs.save(any())).thenAnswer(call -> call.getArgument(0));
        service = new IntakeOcrService(
                documents, jobs, cases, users, audits,
                Clock.fixed(Instant.parse("2026-09-18T02:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void createsOnePendingJobAndReturnsExistingJobOnReplay() {
        var created = service.start(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId);
        var captor = org.mockito.ArgumentCaptor.forClass(OcrJob.class);
        verify(jobs).save(captor.capture());
        OcrJob saved = captor.getValue();
        when(jobs.findByIntakeDocumentVersionId(versionId)).thenReturn(Optional.of(saved));

        var replay = service.start(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId);

        assertEquals(created.ocrJobId(), replay.ocrJobId());
        assertEquals("PENDING", created.state());
        verify(jobs, times(1)).save(any());
        var auditCaptor = org.mockito.ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(audits, times(1)).publish(auditCaptor.capture());
        var audit = auditCaptor.getValue().entries().getFirst();
        assertEquals(BusinessAuditAction.OCR_JOB_CREATED, audit.action());
        assertEquals(BusinessAuditEntityType.OCR_JOB, audit.entityType());
        assertEquals(Set.of("ocrJobId", "assistedOriginationCaseId",
                "intakeDocumentVersionId", "intakeEvidenceType"), audit.payload().values().keySet());
    }

    @Test
    void staleVersionCannotStartOcr() {
        UUID currentVersionId = UUID.randomUUID();
        when(documents.findByCaseAndTypeForUpdate(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION))
                .thenReturn(Optional.of(document(currentVersionId)));

        var exception = assertThrows(BusinessStateConflictException.class,
                () -> service.start(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId));

        assertEquals("OCR_REQUIRES_CURRENT_INTAKE_VERSION", exception.getErrorCode());
        verify(jobs, never()).save(any());
    }

    @Test
    void versionFromAnotherEvidenceRecordIsConcealed() {
        when(documents.findVersionById(versionId))
                .thenReturn(Optional.of(version(versionId, UUID.randomUUID())));

        assertThrows(EntityNotFoundException.class,
                () -> service.start(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId));
        verify(jobs, never()).save(any());
    }

    @Test
    void productMismatchedEvidenceTypeIsRejectedBeforeDocumentAccess() {
        assertThrows(BusinessRuleViolationException.class,
                () -> service.start(caseId, IntakeEvidenceType.COLLATERAL_PAPER_APPLICATION, versionId));
        verify(documents, never()).findByCaseAndTypeForUpdate(any(), any());
    }

    @Test
    void loanOwnedCaseAuthorizationFailureBlocksJobCreation() {
        when(cases.authorizeMutation(caseId)).thenThrow(new BusinessStateConflictException(
                "ASSISTED_ORIGINATION_CASE_NOT_OPEN", "Assisted origination case is no longer open."));

        assertThrows(BusinessStateConflictException.class,
                () -> service.start(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId));
        verify(jobs, never()).save(any());
    }

    @Test
    void getStatusReturnsOnlySafeJobMetadata() {
        OcrJob job = OcrJob.pending(
                version(versionId, documentId), IntakeEvidenceType.UCL_PAPER_APPLICATION,
                UUID.randomUUID(), LocalDateTime.now()
        );
        when(jobs.findByIntakeDocumentVersionId(versionId)).thenReturn(Optional.of(job));

        var dto = service.getStatus(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId);

        assertEquals(job.id(), dto.ocrJobId());
        assertEquals(versionId, dto.intakeDocumentVersionId());
        assertFalse(java.util.Arrays.stream(dto.getClass().getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .anyMatch(name -> name.contains("storage") || name.contains("text") || name.contains("provider")));
    }

    @Test
    void staffWithoutExactPermissionIsRejectedBeforeLoanAuthorization() {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(
                actorId, "loan.officer@meridian.local", "STAFF", null,
                Set.of(), Set.of("document:upload:staff")));

        assertThrows(AuthorizationException.class,
                () -> service.start(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId));
        verify(cases, never()).authorizeMutation(any());
    }

    private IntakeDocument document(UUID currentVersionId) {
        return new IntakeDocument(
                documentId, caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, currentVersionId,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private IntakeDocumentVersion version(UUID id, UUID ownerDocumentId) {
        return new IntakeDocumentVersion(
                id, ownerDocumentId, 1, UUID.randomUUID(), null, "application.pdf",
                "application/pdf", "application/pdf", 100, HASH, "ab/opaque",
                actorId, LocalDateTime.now()
        );
    }
}
