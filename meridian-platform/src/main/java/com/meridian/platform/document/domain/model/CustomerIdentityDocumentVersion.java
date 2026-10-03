package com.meridian.platform.document.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record CustomerIdentityDocumentVersion(UUID id, UUID documentId, int versionNumber,
        UUID uploadRequestId, UUID baselineVersionId, String originalFilename, String declaredMimeType,
        String detectedMimeType, long byteSize, String sha256Hex, String storageKey,
        UUID uploaderUserId, LocalDateTime uploadedAt) {
    public CustomerIdentityDocumentVersion {
        Objects.requireNonNull(id); Objects.requireNonNull(documentId); Objects.requireNonNull(uploadRequestId);
        Objects.requireNonNull(storageKey); Objects.requireNonNull(uploaderUserId); Objects.requireNonNull(uploadedAt);
        if (versionNumber < 1) throw new IllegalArgumentException("versionNumber must be positive");
        DocumentUploadRules.validate(originalFilename, declaredMimeType, detectedMimeType, byteSize, sha256Hex);
    }
    public boolean sameUpload(UUID ownerDocument, UUID baseline, String filename, String mime, long size, String hash, UUID actor) {
        return documentId.equals(ownerDocument) && Objects.equals(baselineVersionId, baseline)
                && originalFilename.equals(filename) && declaredMimeType.equals(mime) && byteSize == size
                && sha256Hex.equals(hash) && uploaderUserId.equals(actor);
    }
    @Override public String toString() { return "CustomerIdentityDocumentVersion[id=" + id + ", versionNumber=" + versionNumber + "]"; }
}
