package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.port.in.CustomerIdentityEvidenceUseCase;
import com.meridian.platform.document.application.port.out.*;
import com.meridian.platform.document.domain.model.*;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class CustomerIdentityEvidenceService implements CustomerIdentityEvidenceUseCase {
    private final CustomerIdentityDocumentRepository digital;
    private final IntakeDocumentRepository intake;
    private final LoanAssistedOriginationPort cases;
    private final DocumentStoragePort storage;
    public CustomerIdentityEvidenceService(CustomerIdentityDocumentRepository digital, IntakeDocumentRepository intake,
            LoanAssistedOriginationPort cases, DocumentStoragePort storage) {
        this.digital = digital; this.intake = intake; this.cases = cases; this.storage = storage;
    }
    @Transactional
    public Evidence store(UUID customerId, UUID requestId, UUID baseline, InputStream content, String mime, String filename, UUID actor, LocalDateTime now) {
        Objects.requireNonNull(requestId);
        digital.lockUploadRequest(requestId);
        StagedDocument staged = storage.stage(content, mime, filename);
        try {
            var document = digital.lockOrCreate(customerId, now);
            var replay = digital.findUpload(requestId).orElse(null);
            if (replay != null) {
                if (!replay.sameUpload(document.id(), baseline, staged.originalFilename(), staged.declaredMimeType(), staged.byteSize(), staged.sha256Hex(), actor))
                    throw new BusinessStateConflictException("IDEMPOTENCY_KEY_REUSED", "The request ID was already used for different identity evidence.");
                return snapshot(replay);
            }
            if (!Objects.equals(document.currentVersionId(), baseline)) throw stale();
            int number = baseline == null ? 1 : digital.findVersion(baseline).orElseThrow(CustomerIdentityEvidenceService::missing).versionNumber() + 1;
            StoredObject stored = storage.commit(staged);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { if (status == STATUS_ROLLED_BACK) storage.deleteFinal(stored.storageKey()); }
            });
            var version = new CustomerIdentityDocumentVersion(UUID.randomUUID(), document.id(), number, requestId, baseline,
                    staged.originalFilename(), staged.declaredMimeType(), staged.detectedMimeType(), staged.byteSize(), staged.sha256Hex(), stored.storageKey(), actor, now);
            digital.saveVersion(version); digital.advance(document.id(), version.id(), now);
            return snapshot(version);
        } finally { storage.discardStaged(staged); }
    }
    @Transactional
    public UUID lockIntakeCustomer(UUID caseId, boolean requireOpen) { return cases.authorizeIdentityVerification(caseId, requireOpen); }
    @Transactional
    public Evidence requireCurrent(UUID customerId, UUID caseId, UUID versionId) {
        if (caseId == null) {
            var doc = digital.find(customerId, true).orElseThrow(CustomerIdentityEvidenceService::missing);
            if (!versionId.equals(doc.currentVersionId())) throw stale();
        } else {
            var doc = intake.findByCaseAndTypeForUpdate(caseId, IntakeEvidenceType.CUSTOMER_IDENTITY).orElseThrow(CustomerIdentityEvidenceService::missing);
            if (!versionId.equals(doc.currentVersionId())) throw stale();
        }
        return metadata(customerId, caseId, versionId);
    }
    @Transactional(readOnly = true)
    public Evidence metadata(UUID customerId, UUID caseId, UUID versionId) {
        if (caseId == null) {
            var doc = digital.find(customerId, false).orElseThrow(CustomerIdentityEvidenceService::missing);
            return snapshot(digital.findVersion(versionId).filter(v -> v.documentId().equals(doc.id())).orElseThrow(CustomerIdentityEvidenceService::missing));
        }
        var doc = intake.findByCaseAndType(caseId, IntakeEvidenceType.CUSTOMER_IDENTITY).orElseThrow(CustomerIdentityEvidenceService::missing);
        var v = intake.findVersionById(versionId).filter(candidate -> candidate.intakeDocumentId().equals(doc.id())).orElseThrow(CustomerIdentityEvidenceService::missing);
        return new Evidence(v.id(), v.versionNumber(), v.originalFilename(), v.detectedMimeType(), v.byteSize(), v.uploadedAt());
    }
    @Transactional(readOnly = true)
    public Content read(UUID customerId, UUID caseId, UUID versionId) {
        Evidence metadata = metadata(customerId, caseId, versionId);
        String key = caseId == null ? digital.findVersion(versionId).orElseThrow().storageKey() : intake.findVersionById(versionId).orElseThrow().storageKey();
        return new Content(metadata.filename(), metadata.mimeType(), metadata.byteSize(), storage.open(key));
    }
    private static Evidence snapshot(CustomerIdentityDocumentVersion v) { return new Evidence(v.id(), v.versionNumber(), v.originalFilename(), v.detectedMimeType(), v.byteSize(), v.uploadedAt()); }
    private static EntityNotFoundException missing() { return new EntityNotFoundException("IDENTITY_EVIDENCE_NOT_FOUND", "Identity evidence was not found."); }
    private static BusinessStateConflictException stale() { return new BusinessStateConflictException("IDENTITY_VERIFICATION_EVIDENCE_STALE", "Identity evidence has changed. Submit its current version for review."); }
}
