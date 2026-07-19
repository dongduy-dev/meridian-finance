package com.meridian.platform.loan.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record StaffCorrectionTaskDto(
        UUID taskId,
        UUID correctionRequestId,
        UUID loanApplicationId,
        String status,
        String scope,
        String documentType,
        UUID checklistItemId,
        UUID baselineDocumentVersionId,
        String reasonCode,
        String staffInstruction,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
