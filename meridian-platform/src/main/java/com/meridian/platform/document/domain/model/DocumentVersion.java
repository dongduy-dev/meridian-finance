package com.meridian.platform.document.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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
    public static final long MAX_BYTE_SIZE = 10L * 1024L * 1024L;
    public static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png"
    );
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public DocumentVersion {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        if (versionNumber <= 0) {
            throw invalid("Document version number must be positive.");
        }
        Objects.requireNonNull(uploadRequestId, "uploadRequestId must not be null");
        validateFilename(originalFilename);
        if (!ALLOWED_MIME_TYPES.contains(declaredMimeType)
                || !Objects.equals(declaredMimeType, detectedMimeType)) {
            throw invalid("Declared and detected document MIME types must match the allowlist.");
        }
        if (byteSize <= 0 || byteSize > MAX_BYTE_SIZE) {
            throw invalid("Document size must be between 1 byte and 10 MiB.");
        }
        if (sha256Hex == null || !SHA_256_PATTERN.matcher(sha256Hex).matches()) {
            throw invalid("Document SHA-256 value is malformed.");
        }
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

    private static void validateFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > 255
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")
                || filename.chars().anyMatch(Character::isISOControl)) {
            throw invalid("Original filename is invalid.");
        }
    }

    private static BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException("INVALID_DOCUMENT_UPLOAD", message);
    }
}
