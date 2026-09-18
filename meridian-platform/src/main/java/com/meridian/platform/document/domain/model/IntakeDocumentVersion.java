package com.meridian.platform.document.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record IntakeDocumentVersion(
        UUID id,
        UUID intakeDocumentId,
        int versionNumber,
        UUID uploadRequestId,
        UUID baselineVersionId,
        String originalFilename,
        String declaredMimeType,
        String detectedMimeType,
        long byteSize,
        String sha256Hex,
        String storageKey,
        UUID uploaderStaffUserId,
        LocalDateTime uploadedAt
) {
    public IntakeDocumentVersion {
        Objects.requireNonNull(id);
        Objects.requireNonNull(intakeDocumentId);
        if (versionNumber <= 0) throw new IllegalArgumentException("versionNumber must be positive");
        Objects.requireNonNull(uploadRequestId);
        DocumentUploadRules.validate(originalFilename, declaredMimeType, detectedMimeType, byteSize, sha256Hex);
        Objects.requireNonNull(storageKey);
        Objects.requireNonNull(uploaderStaffUserId);
        Objects.requireNonNull(uploadedAt);
    }

    public boolean sameLogicalUpload(
            UUID documentId, UUID baselineId, String filename, String mimeType,
            long size, String hash, UUID uploaderId
    ) {
        return intakeDocumentId.equals(documentId)
                && Objects.equals(baselineVersionId, baselineId)
                && originalFilename.equals(filename)
                && declaredMimeType.equals(mimeType)
                && byteSize == size
                && sha256Hex.equals(hash)
                && uploaderStaffUserId.equals(uploaderId);
    }
}
