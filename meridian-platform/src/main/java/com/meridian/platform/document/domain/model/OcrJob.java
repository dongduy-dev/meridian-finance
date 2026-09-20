package com.meridian.platform.document.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record OcrJob(
        UUID id,
        UUID intakeDocumentVersionId,
        IntakeEvidenceType evidenceType,
        String sourceStorageKey,
        String sourceMimeType,
        String sourceSha256Hex,
        OcrJobState state,
        String leaseOwner,
        LocalDateTime leaseExpiresAt,
        int attemptCount,
        LocalDateTime nextAttemptAt,
        OcrFailureCategory failureCategory,
        UUID traceId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        LocalDateTime failedAt
) {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public OcrJob {
        Objects.requireNonNull(id);
        Objects.requireNonNull(intakeDocumentVersionId);
        Objects.requireNonNull(evidenceType);
        Objects.requireNonNull(sourceStorageKey);
        Objects.requireNonNull(sourceMimeType);
        Objects.requireNonNull(sourceSha256Hex);
        Objects.requireNonNull(state);
        Objects.requireNonNull(nextAttemptAt);
        Objects.requireNonNull(traceId);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (!DocumentVersion.ALLOWED_MIME_TYPES.contains(sourceMimeType)) {
            throw new IllegalArgumentException("sourceMimeType must be an allowed document MIME type");
        }
        if (!SHA256.matcher(sourceSha256Hex).matches()) {
            throw new IllegalArgumentException("sourceSha256Hex must be a lowercase SHA-256 value");
        }
        if (attemptCount < 0) throw new IllegalArgumentException("attemptCount must not be negative");
        boolean leased = leaseOwner != null || leaseExpiresAt != null;
        if ((state == OcrJobState.PROCESSING) != (leaseOwner != null && leaseExpiresAt != null) ||
                (state != OcrJobState.PROCESSING && leased)) {
            throw new IllegalArgumentException("Only processing OCR jobs may hold a complete lease");
        }
        if ((state == OcrJobState.COMPLETED) != (completedAt != null)) {
            throw new IllegalArgumentException("Only completed OCR jobs require completedAt");
        }
        if ((state == OcrJobState.FAILED) != (failedAt != null)) {
            throw new IllegalArgumentException("Only failed OCR jobs require failedAt");
        }
        if (state == OcrJobState.FAILED && failureCategory == null) {
            throw new IllegalArgumentException("Failed OCR jobs require a controlled failure category");
        }
    }

    public static OcrJob pending(IntakeDocumentVersion version, IntakeEvidenceType evidenceType,
                                 UUID traceId, LocalDateTime now) {
        return new OcrJob(
                UUID.randomUUID(), version.id(), evidenceType, version.storageKey(),
                version.detectedMimeType(), version.sha256Hex(), OcrJobState.PENDING,
                null, null, 0, now, null, traceId, now, now, null, null
        );
    }
}
