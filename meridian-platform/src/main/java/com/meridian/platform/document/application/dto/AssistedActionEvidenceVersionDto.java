package com.meridian.platform.document.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AssistedActionEvidenceVersionDto(
        UUID documentVersionId,
        int versionNumber,
        String detectedMimeType,
        long byteSize,
        LocalDateTime uploadedAt
) {
}
