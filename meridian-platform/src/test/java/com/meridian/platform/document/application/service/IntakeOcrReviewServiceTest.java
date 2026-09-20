package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.FinalizeIntakeOcrReviewRequest;
import com.meridian.platform.document.application.port.out.IntakeDocumentRepository;
import com.meridian.platform.document.application.port.out.LoanAssistedOriginationPort;
import com.meridian.platform.document.application.port.out.OcrJobRepository;
import com.meridian.platform.document.application.port.out.OcrResultCipher;
import com.meridian.platform.document.application.port.out.OcrResultRepository;
import com.meridian.platform.document.application.port.out.OcrReviewRepository;
import com.meridian.platform.document.domain.model.IntakeDocument;
import com.meridian.platform.document.domain.model.IntakeDocumentVersion;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.document.domain.model.OcrJob;
import com.meridian.platform.document.domain.model.OcrJobState;
import com.meridian.platform.document.domain.model.OcrResult;
import com.meridian.platform.document.domain.model.OcrResultDisposition;
import com.meridian.platform.document.domain.model.OcrReview;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeOcrReviewServiceTest {
    private static final String HASH = "a".repeat(64);
    private final IntakeDocumentRepository documents = mock(IntakeDocumentRepository.class);
    private final OcrJobRepository jobs = mock(OcrJobRepository.class);
    private final OcrResultRepository results = mock(OcrResultRepository.class);
    private final OcrReviewRepository reviews = mock(OcrReviewRepository.class);
    private final OcrResultCipher cipher = mock(OcrResultCipher.class);
    private final LoanAssistedOriginationPort cases = mock(LoanAssistedOriginationPort.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final BusinessAuditPublisher audits = mock(BusinessAuditPublisher.class);
    private final UUID caseId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID resultId = UUID.randomUUID();
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 20, 10, 0);
    private OcrResult result;
    private IntakeOcrReviewService service;

    @BeforeEach
    void setUp() {
        result = new OcrResult(resultId, jobId, "encrypted-suggestions",
                OcrResultDisposition.PENDING_REVIEW, now);
        when(users.currentUser()).thenReturn(new AuthenticatedUser(
                actorId, "staff@meridian.local", "STAFF", null,
                Set.of(), Set.of("document:upload:intake")));
        when(cases.authorizeRead(caseId)).thenReturn(
                new LoanAssistedOriginationPort.AuthorizedIntake(caseId, "UNSECURED_CONSUMER_LOAN"));
        when(cases.authorizeMutation(caseId)).thenReturn(
                new LoanAssistedOriginationPort.AuthorizedIntake(caseId, "UNSECURED_CONSUMER_LOAN"));
        when(documents.findByCaseAndType(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION))
                .thenReturn(Optional.of(document(versionId)));
        when(documents.findByCaseAndTypeForUpdate(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION))
                .thenReturn(Optional.of(document(versionId)));
        when(documents.findVersionById(versionId)).thenReturn(Optional.of(version(documentId)));
        when(jobs.findByIntakeDocumentVersionId(versionId)).thenReturn(Optional.of(completedJob()));
        when(results.findByJobId(jobId)).thenReturn(Optional.of(result));
        when(results.findByIdForUpdate(resultId)).thenReturn(Optional.of(result));
        when(results.save(any())).thenAnswer(call -> call.getArgument(0));
        when(reviews.save(any())).thenAnswer(call -> call.getArgument(0));
        when(cipher.decrypt("encrypted-suggestions")).thenReturn(
                "[{\"fieldName\":\"fullName\",\"proposedValue\":\"OCR Name\",\"confidence\":0.93},"
                        + "{\"fieldName\":\"termsConsentAccepted\",\"proposedValue\":\"true\",\"confidence\":0.99}]"
        );
        when(cipher.encrypt(any())).thenReturn("v1:gcm:nonce:ciphertext");
        when(cipher.decrypt("v1:gcm:nonce:ciphertext")).thenReturn(
                "{\"accountNumber\":\"1234567890\",\"fullName\":\"Corrected Name\"}"
        );
        service = new IntakeOcrReviewService(
                documents, jobs, results, reviews, cipher, cases, users, audits,
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void disclosesHistoricalResultWithOnlyAllowlistedSuggestionsAndNoRawInternals() {
        when(documents.findByCaseAndType(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION))
                .thenReturn(Optional.of(document(UUID.randomUUID())));

        var dto = service.get(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId);

        assertEquals(resultId, dto.ocrResultId());
        assertEquals(1, dto.suggestions().size());
        assertEquals("fullName", dto.suggestions().getFirst().fieldName());
        assertFalse(java.util.Arrays.stream(dto.getClass().getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .anyMatch(name -> name.contains("text") || name.contains("layout")
                        || name.contains("storage") || name.contains("provider")
                        || name.contains("encrypted")));
    }

    @Test
    void finalizesEncryptedReviewAndMovesDispositionWithPiiSafeAudit() {
        var dto = service.finalizeReview(
                caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId,
                new FinalizeIntakeOcrReviewRequest(resultId, Map.of(
                        "fullName", "Corrected Name", "accountNumber", "1234567890"
                ))
        );

        assertEquals("REVIEWED", dto.disposition());
        verify(cipher).encrypt("{\"accountNumber\":\"1234567890\",\"fullName\":\"Corrected Name\"}");
        var reviewCaptor = org.mockito.ArgumentCaptor.forClass(OcrReview.class);
        verify(reviews).save(reviewCaptor.capture());
        assertEquals(OcrResultDisposition.PENDING_REVIEW,
                reviewCaptor.getValue().sourceDisposition());
        verify(results).save(result.reviewed());
        var auditCaptor = org.mockito.ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(audits).publish(auditCaptor.capture());
        var audit = auditCaptor.getValue().entries().getFirst();
        assertEquals(BusinessAuditAction.OCR_RESULT_REVIEWED, audit.action());
        assertEquals(BusinessAuditEntityType.OCR_REVIEW, audit.entityType());
        assertEquals(Set.of("ocrReviewId", "ocrResultId", "assistedOriginationCaseId",
                "intakeDocumentVersionId", "intakeEvidenceType"), audit.payload().values().keySet());
        assertFalse(audit.payload().values().toString().contains("Corrected Name"));
        assertFalse(audit.payload().values().toString().contains("1234567890"));
    }

    @Test
    void allowsAnEmptyFinalReviewWhenNoOcrValueIsUsable() {
        service.finalizeReview(
                caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId,
                new FinalizeIntakeOcrReviewRequest(resultId, Map.of())
        );

        verify(cipher).encrypt("{}");
        verify(reviews).save(any(OcrReview.class));
        verify(results).save(result.reviewed());
    }

    @Test
    void exactFinalReviewReplayReturnsExistingAndChangedReplayConflicts() {
        OcrReview existing = new OcrReview(
                UUID.randomUUID(), resultId, actorId, OcrResultDisposition.PENDING_REVIEW,
                "encrypted-review", now
        );
        when(reviews.findByOcrResultId(resultId)).thenReturn(Optional.of(existing));
        when(cipher.decrypt("encrypted-review")).thenReturn("{\"fullName\":\"Reviewed Name\"}");

        var replay = service.finalizeReview(
                caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId,
                new FinalizeIntakeOcrReviewRequest(resultId, Map.of("fullName", "Reviewed Name"))
        );
        assertEquals(Map.of("fullName", "Reviewed Name"), replay.reviewedFields());
        assertThrows(BusinessStateConflictException.class, () -> service.finalizeReview(
                caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId,
                new FinalizeIntakeOcrReviewRequest(resultId, Map.of("fullName", "Changed"))
        ));
        verify(reviews, never()).save(any());
    }

    @Test
    void rejectsStaleVersionUnknownFieldMismatchAndIncompleteJob() {
        when(documents.findByCaseAndTypeForUpdate(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION))
                .thenReturn(Optional.of(document(UUID.randomUUID())));
        assertThrows(BusinessStateConflictException.class, () -> service.finalizeReview(
                caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId,
                new FinalizeIntakeOcrReviewRequest(resultId, Map.of())
        ));

        when(documents.findByCaseAndTypeForUpdate(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION))
                .thenReturn(Optional.of(document(versionId)));
        assertThrows(BusinessRuleViolationException.class, () -> service.finalizeReview(
                caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId,
                new FinalizeIntakeOcrReviewRequest(resultId, Map.of("termsConsentAccepted", "true"))
        ));

        when(jobs.findByIntakeDocumentVersionId(versionId)).thenReturn(Optional.of(pendingJob()));
        assertThrows(BusinessStateConflictException.class,
                () -> service.get(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId));
    }

    @Test
    void concealsEvidenceVersionAndResultMismatch() {
        when(documents.findVersionById(versionId)).thenReturn(Optional.of(version(UUID.randomUUID())));
        assertThrows(EntityNotFoundException.class,
                () -> service.get(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId));

        when(documents.findVersionById(versionId)).thenReturn(Optional.of(version(documentId)));
        assertThrows(BusinessStateConflictException.class, () -> service.finalizeReview(
                caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId,
                new FinalizeIntakeOcrReviewRequest(UUID.randomUUID(), Map.of())
        ));
    }

    private IntakeDocument document(UUID currentVersionId) {
        return new IntakeDocument(documentId, caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION,
                currentVersionId, now, now);
    }

    private IntakeDocumentVersion version(UUID owner) {
        return new IntakeDocumentVersion(versionId, owner, 1, UUID.randomUUID(), null,
                "application.pdf", "application/pdf", "application/pdf", 100, HASH,
                "ab/opaque", actorId, now);
    }

    private OcrJob completedJob() {
        return new OcrJob(jobId, versionId, IntakeEvidenceType.UCL_PAPER_APPLICATION,
                "ab/opaque", "application/pdf", HASH, OcrJobState.COMPLETED,
                null, null, 1, now, null, UUID.randomUUID(), now, now, now, null);
    }

    private OcrJob pendingJob() {
        return new OcrJob(jobId, versionId, IntakeEvidenceType.UCL_PAPER_APPLICATION,
                "ab/opaque", "application/pdf", HASH, OcrJobState.PENDING,
                null, null, 0, now, null, UUID.randomUUID(), now, now, null, null);
    }
}
