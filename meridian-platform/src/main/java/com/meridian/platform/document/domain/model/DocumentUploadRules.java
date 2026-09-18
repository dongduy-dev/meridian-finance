package com.meridian.platform.document.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class DocumentUploadRules {

    public static final long MAX_BYTE_SIZE = 10L * 1024L * 1024L;
    public static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png"
    );
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private DocumentUploadRules() {
    }

    public static void validate(
            String filename, String declaredMimeType, String detectedMimeType,
            long byteSize, String sha256Hex
    ) {
        if (filename == null || filename.isBlank() || filename.length() > 255
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")
                || filename.chars().anyMatch(Character::isISOControl)) {
            throw invalid("Original filename is invalid.");
        }
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
    }

    private static BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException("INVALID_DOCUMENT_UPLOAD", message);
    }
}
