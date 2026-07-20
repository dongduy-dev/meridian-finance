package com.meridian.platform.document.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentVersionDto(
        UUID documentVersionId,
        UUID checklistItemId,
        int versionNumber,
        String originalFilename,
        String mimeType,
        long byteSize,
        LocalDateTime uploadedAt
) {
}
