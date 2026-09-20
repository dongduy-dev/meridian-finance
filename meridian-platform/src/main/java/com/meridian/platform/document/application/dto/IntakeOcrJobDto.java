package com.meridian.platform.document.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record IntakeOcrJobDto(
        UUID ocrJobId,
        UUID intakeDocumentVersionId,
        String state,
        String disposition,
        int attemptCount,
        String failureCategory,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        LocalDateTime failedAt
) {
}
