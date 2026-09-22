package com.meridian.platform.loan.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AssistedActionEvidenceMetadataDto(
        UUID documentId,
        UUID documentVersionId,
        String evidenceType,
        String declaredOfferDecision,
        UUID targetId,
        Integer targetVersion,
        int versionNumber,
        String detectedMimeType,
        long byteSize,
        LocalDateTime uploadedAt
) {
}
