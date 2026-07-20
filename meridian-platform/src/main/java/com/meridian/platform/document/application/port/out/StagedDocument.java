package com.meridian.platform.document.application.port.out;

import java.util.Objects;
import java.util.UUID;

public record StagedDocument(
        UUID stagingId,
        String originalFilename,
        String declaredMimeType,
        String detectedMimeType,
        long byteSize,
        String sha256Hex
) {
    public StagedDocument {
        Objects.requireNonNull(stagingId, "stagingId must not be null");
        Objects.requireNonNull(originalFilename, "originalFilename must not be null");
        Objects.requireNonNull(declaredMimeType, "declaredMimeType must not be null");
        Objects.requireNonNull(detectedMimeType, "detectedMimeType must not be null");
        Objects.requireNonNull(sha256Hex, "sha256Hex must not be null");
    }
}
