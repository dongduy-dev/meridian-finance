package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.IntakeEvidenceDto;
import com.meridian.platform.document.application.dto.IntakeEvidenceVersionDto;
import com.meridian.platform.document.application.dto.UploadIntakeEvidenceCommand;
import com.meridian.platform.document.application.port.in.ManageIntakeEvidenceUseCase;
import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.application.port.out.IntakeDocumentRepository;
import com.meridian.platform.document.application.port.out.LoanAssistedOriginationPort;
import com.meridian.platform.document.application.port.out.StagedDocument;
import com.meridian.platform.document.application.port.out.StoredObject;
import com.meridian.platform.document.domain.model.IntakeDocument;
import com.meridian.platform.document.domain.model.IntakeDocumentVersion;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class IntakeEvidenceService implements ManageIntakeEvidenceUseCase {

    private static final String PERMISSION = "document:upload:intake";

    private final IntakeDocumentRepository documents;
    private final DocumentStoragePort storage;
    private final LoanAssistedOriginationPort assistedOriginations;
    private final CurrentUserProvider currentUsers;
    private final BusinessAuditPublisher auditPublisher;
    private final Clock clock;

    public IntakeEvidenceService(
            IntakeDocumentRepository documents,
            DocumentStoragePort storage,
            LoanAssistedOriginationPort assistedOriginations,
            CurrentUserProvider currentUsers,
            BusinessAuditPublisher auditPublisher,
            Clock clock
    ) {
        this.documents = documents;
        this.storage = storage;
        this.assistedOriginations = assistedOriginations;
        this.currentUsers = currentUsers;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IntakeEvidenceDto> findEvidence(UUID caseId) {
        requireStaff();
        assistedOriginations.authorizeRead(caseId);
        return documents.findByCase(caseId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public IntakeEvidenceVersionDto upload(UploadIntakeEvidenceCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        StagedDocument staged = storage.stage(
                command.content(), command.declaredMimeType(), command.originalFilename());
        try {
            return store(command, staged);
        } finally {
            storage.discardStaged(staged);
        }
    }

    protected IntakeEvidenceVersionDto store(UploadIntakeEvidenceCommand command, StagedDocument staged) {
        AuthenticatedUser actor = requireStaff();
        Objects.requireNonNull(command.assistedOriginationCaseId());
        Objects.requireNonNull(command.evidenceType());
        Objects.requireNonNull(command.uploadRequestId());
        var authorized = assistedOriginations.authorizeMutation(command.assistedOriginationCaseId());
        command.evidenceType().requireProduct(authorized.productCode());
        LocalDateTime now = LocalDateTime.now(clock);

        IntakeDocument document = documents.findByCaseAndTypeForUpdate(
                        command.assistedOriginationCaseId(), command.evidenceType())
                .orElseGet(() -> documents.saveDocument(new IntakeDocument(
                        UUID.randomUUID(), command.assistedOriginationCaseId(), command.evidenceType(),
                        null, now, now
                )));

        IntakeDocumentVersion replay = documents.findVersionByUploadRequestId(command.uploadRequestId())
                .orElse(null);
        if (replay != null) {
            if (!replay.sameLogicalUpload(
                    document.id(), command.expectedCurrentVersionId(), staged.originalFilename(),
                    staged.declaredMimeType(), staged.byteSize(), staged.sha256Hex(), actor.userId())) {
                throw new BusinessStateConflictException("IDEMPOTENCY_KEY_REUSED",
                        "The request ID was already used for different intake evidence.");
            }
            return toVersionDto(replay);
        }
        if (!Objects.equals(document.currentVersionId(), command.expectedCurrentVersionId())) {
            throw new BusinessStateConflictException("STALE_DOCUMENT_VERSION",
                    "The intake evidence current version changed before this upload was stored.");
        }
        int versionNumber = document.currentVersionId() == null ? 1
                : documents.findVersionById(document.currentVersionId())
                        .orElseThrow(() -> new IllegalStateException("Current intake evidence version was not found."))
                        .versionNumber() + 1;

        StoredObject stored = storage.commit(staged);
        registerRollbackCleanup(stored.storageKey());
        IntakeDocumentVersion version = documents.saveVersion(new IntakeDocumentVersion(
                UUID.randomUUID(), document.id(), versionNumber, command.uploadRequestId(),
                command.expectedCurrentVersionId(), staged.originalFilename(), staged.declaredMimeType(),
                staged.detectedMimeType(), staged.byteSize(), staged.sha256Hex(), stored.storageKey(),
                actor.userId(), now
        ));
        documents.saveDocument(document.withCurrentVersion(version.id(), now));
        auditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), now),
                new BusinessAuditEntry(
                        BusinessAuditAction.INTAKE_DOCUMENT_VERSION_UPLOADED,
                        BusinessAuditEntityType.INTAKE_DOCUMENT_VERSION,
                        version.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.ASSISTED_ORIGINATION_CASE_ID,
                                        command.assistedOriginationCaseId())
                                .put(BusinessAuditPayloadKey.INTAKE_DOCUMENT_VERSION_ID, version.id())
                                .put(BusinessAuditPayloadKey.INTAKE_EVIDENCE_TYPE, command.evidenceType())
                                .build()
                )
        ));
        return toVersionDto(version);
    }

    private AuthenticatedUser requireStaff() {
        AuthenticatedUser actor = currentUsers.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission(PERMISSION)) {
            throw new AuthorizationException("INTAKE_EVIDENCE_ACCESS_DENIED",
                    "Staff intake evidence access is denied.");
        }
        return actor;
    }

    private IntakeEvidenceDto toDto(IntakeDocument document) {
        return new IntakeEvidenceDto(
                document.id(), document.assistedOriginationCaseId(), document.evidenceType().name(),
                document.currentVersionId(),
                documents.findVersionsByDocumentId(document.id()).stream()
                        .map(IntakeEvidenceService::toVersionDto).toList()
        );
    }

    private static IntakeEvidenceVersionDto toVersionDto(IntakeDocumentVersion version) {
        return new IntakeEvidenceVersionDto(
                version.id(), version.versionNumber(), version.originalFilename(),
                version.detectedMimeType(), version.byteSize(), version.uploadedAt()
        );
    }

    private void registerRollbackCleanup(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) storage.deleteFinal(storageKey);
            }
        });
    }
}
