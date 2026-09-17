package com.meridian.platform.document.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record IntakeEvidenceVersionDto(
        UUID intakeDocumentVersionId,
        int versionNumber,
        String originalFilename,
        String detectedMimeType,
        long byteSize,
        LocalDateTime uploadedAt
) {
}
