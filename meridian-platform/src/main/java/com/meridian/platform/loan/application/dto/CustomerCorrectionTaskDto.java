package com.meridian.platform.loan.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerCorrectionTaskDto(
        UUID correctionTaskId,
        UUID correctionRequestId,
        String status,
        String scope,
        String documentType,
        UUID checklistItemId,
        String reasonCode,
        String customerInstruction,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
