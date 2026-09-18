package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.document.domain.model.OcrFailureCategory;
import com.meridian.platform.document.domain.model.OcrJob;
import com.meridian.platform.document.domain.model.OcrJobState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ocr_jobs")
public class OcrJobJpaEntity {

    @Id
    private UUID id;

    @Column(name = "intake_document_version_id", nullable = false, unique = true)
    private UUID intakeDocumentVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false)
    private IntakeEvidenceType evidenceType;

    @Column(name = "source_storage_key", nullable = false)
    private String sourceStorageKey;

    @Column(name = "source_mime_type", nullable = false)
    private String sourceMimeType;

    @Column(name = "source_sha256_hex", nullable = false)
    private String sourceSha256Hex;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private OcrJobState state;

    @Column(name = "lease_owner")
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category")
    private OcrFailureCategory failureCategory;

    @Column(name = "trace_id", nullable = false)
    private UUID traceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    protected OcrJobJpaEntity() {
    }

    OcrJobJpaEntity(OcrJob job) {
        update(job);
    }

    void update(OcrJob job) {
        id = job.id();
        intakeDocumentVersionId = job.intakeDocumentVersionId();
        evidenceType = job.evidenceType();
        sourceStorageKey = job.sourceStorageKey();
        sourceMimeType = job.sourceMimeType();
        sourceSha256Hex = job.sourceSha256Hex();
        state = job.state();
        leaseOwner = job.leaseOwner();
        leaseExpiresAt = job.leaseExpiresAt();
        attemptCount = job.attemptCount();
        nextAttemptAt = job.nextAttemptAt();
        failureCategory = job.failureCategory();
        traceId = job.traceId();
        createdAt = job.createdAt();
        updatedAt = job.updatedAt();
        completedAt = job.completedAt();
        failedAt = job.failedAt();
    }

    OcrJob toDomain() {
        return new OcrJob(
                id, intakeDocumentVersionId, evidenceType, sourceStorageKey,
                sourceMimeType, sourceSha256Hex, state, leaseOwner, leaseExpiresAt,
                attemptCount, nextAttemptAt, failureCategory, traceId, createdAt,
                updatedAt, completedAt, failedAt
        );
    }
}
