package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.IntakeOcrJobDto;
import com.meridian.platform.document.application.port.in.ManageIntakeOcrUseCase;
import com.meridian.platform.document.application.port.out.IntakeDocumentRepository;
import com.meridian.platform.document.application.port.out.LoanAssistedOriginationPort;
import com.meridian.platform.document.application.port.out.OcrJobRepository;
import com.meridian.platform.document.domain.model.IntakeDocument;
import com.meridian.platform.document.domain.model.IntakeDocumentVersion;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.document.domain.model.OcrJob;
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
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class IntakeOcrService implements ManageIntakeOcrUseCase {

    private static final String PERMISSION = "document:upload:intake";

    private final IntakeDocumentRepository documents;
    private final OcrJobRepository jobs;
    private final LoanAssistedOriginationPort assistedOriginations;
    private final CurrentUserProvider currentUsers;
    private final BusinessAuditPublisher auditPublisher;
    private final Clock clock;

    public IntakeOcrService(
            IntakeDocumentRepository documents,
            OcrJobRepository jobs,
            LoanAssistedOriginationPort assistedOriginations,
            CurrentUserProvider currentUsers,
            BusinessAuditPublisher auditPublisher,
            Clock clock
    ) {
        this.documents = documents;
        this.jobs = jobs;
        this.assistedOriginations = assistedOriginations;
        this.currentUsers = currentUsers;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IntakeOcrJobDto start(UUID caseId, IntakeEvidenceType evidenceType, UUID versionId) {
        AuthenticatedUser actor = requireStaff();
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(evidenceType);
        Objects.requireNonNull(versionId);
        var authorized = assistedOriginations.authorizeMutation(caseId);
        evidenceType.requireProduct(authorized.productCode());
        IntakeDocument document = documents.findByCaseAndTypeForUpdate(caseId, evidenceType)
                .orElseThrow(IntakeOcrService::evidenceNotFound);
        IntakeDocumentVersion version = requireRelatedVersion(document, versionId);
        if (!versionId.equals(document.currentVersionId())) {
            throw new BusinessStateConflictException(
                    "OCR_REQUIRES_CURRENT_INTAKE_VERSION",
                    "OCR processing may be started only for the current intake evidence version."
            );
        }
        OcrJob existing = jobs.findByIntakeDocumentVersionId(versionId).orElse(null);
        if (existing != null) return toDto(existing);

        LocalDateTime now = LocalDateTime.now(clock);
        UUID traceId = UUID.randomUUID();
        OcrJob job = jobs.save(OcrJob.pending(version, evidenceType, traceId, now));
        auditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(traceId, actor.userId(), now),
                new BusinessAuditEntry(
                        BusinessAuditAction.OCR_JOB_CREATED,
                        BusinessAuditEntityType.OCR_JOB,
                        job.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.OCR_JOB_ID, job.id())
                                .put(BusinessAuditPayloadKey.ASSISTED_ORIGINATION_CASE_ID, caseId)
                                .put(BusinessAuditPayloadKey.INTAKE_DOCUMENT_VERSION_ID, versionId)
                                .put(BusinessAuditPayloadKey.INTAKE_EVIDENCE_TYPE, evidenceType)
                                .build()
                )
        ));
        return toDto(job);
    }

    @Override
    @Transactional(readOnly = true)
    public IntakeOcrJobDto getStatus(UUID caseId, IntakeEvidenceType evidenceType, UUID versionId) {
        requireStaff();
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(evidenceType);
        Objects.requireNonNull(versionId);
        var authorized = assistedOriginations.authorizeRead(caseId);
        evidenceType.requireProduct(authorized.productCode());
        IntakeDocument document = documents.findByCaseAndType(caseId, evidenceType)
                .orElseThrow(IntakeOcrService::evidenceNotFound);
        requireRelatedVersion(document, versionId);
        return jobs.findByIntakeDocumentVersionId(versionId)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException(
                        "OCR_JOB_NOT_FOUND", "OCR job was not found."
                ));
    }

    private IntakeDocumentVersion requireRelatedVersion(IntakeDocument document, UUID versionId) {
        return documents.findVersionById(versionId)
                .filter(version -> version.intakeDocumentId().equals(document.id()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "INTAKE_EVIDENCE_VERSION_NOT_FOUND", "Intake evidence version was not found."
                ));
    }

    private AuthenticatedUser requireStaff() {
        AuthenticatedUser actor = currentUsers.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission(PERMISSION)) {
            throw new AuthorizationException("INTAKE_OCR_ACCESS_DENIED",
                    "Staff intake OCR access is denied.");
        }
        return actor;
    }

    private static EntityNotFoundException evidenceNotFound() {
        return new EntityNotFoundException(
                "INTAKE_EVIDENCE_NOT_FOUND", "Intake evidence was not found."
        );
    }

    private IntakeOcrJobDto toDto(OcrJob job) {
        return new IntakeOcrJobDto(
                job.id(), job.intakeDocumentVersionId(), job.state().name(),
                jobs.findDispositionByJobId(job.id()).map(Enum::name).orElse(null),
                job.attemptCount(),
                job.failureCategory() == null ? null : job.failureCategory().name(),
                job.createdAt(), job.updatedAt(), job.completedAt(), job.failedAt()
        );
    }
}
