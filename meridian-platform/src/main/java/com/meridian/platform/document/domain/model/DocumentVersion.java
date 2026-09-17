package com.meridian.platform.document.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record DocumentVersion(
        UUID id,
        UUID documentId,
        int versionNumber,
        UUID uploadRequestId,
        UUID baselineDocumentVersionId,
        String originalFilename,
        String declaredMimeType,
        String detectedMimeType,
        long byteSize,
        String sha256Hex,
        String storageKey,
        DocumentUploaderActorType uploaderActorType,
        UUID uploaderUserId,
        LocalDateTime uploadedAt
) {
    public static final long MAX_BYTE_SIZE = DocumentUploadRules.MAX_BYTE_SIZE;
    public static final java.util.Set<String> ALLOWED_MIME_TYPES = DocumentUploadRules.ALLOWED_MIME_TYPES;

    public DocumentVersion {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        if (versionNumber <= 0) {
            throw invalid("Document version number must be positive.");
        }
        Objects.requireNonNull(uploadRequestId, "uploadRequestId must not be null");
        DocumentUploadRules.validate(originalFilename, declaredMimeType, detectedMimeType, byteSize, sha256Hex);
        Objects.requireNonNull(storageKey, "storageKey must not be null");
        Objects.requireNonNull(uploaderActorType, "uploaderActorType must not be null");
        Objects.requireNonNull(uploaderUserId, "uploaderUserId must not be null");
        Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
    }

    public boolean sameLogicalUpload(
            UUID expectedDocumentId,
            UUID expectedBaselineVersionId,
            String expectedFilename,
            String expectedMimeType,
            long expectedByteSize,
            String expectedSha256,
            UUID expectedUploaderUserId
    ) {
        return documentId.equals(expectedDocumentId)
                && Objects.equals(baselineDocumentVersionId, expectedBaselineVersionId)
                && originalFilename.equals(expectedFilename)
                && declaredMimeType.equals(expectedMimeType)
                && byteSize == expectedByteSize
                && sha256Hex.equals(expectedSha256)
                && uploaderUserId.equals(expectedUploaderUserId);
    }

    private static BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException("INVALID_DOCUMENT_UPLOAD", message);
    }

}
