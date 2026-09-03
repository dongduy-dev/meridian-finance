package com.meridian.platform.loan.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffCorrectionCaseDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String applicationStatus,
        CorrectionRequestDto correctionRequest
) {
    public record CorrectionRequestDto(
            UUID correctionRequestId,
            String status,
            String reasonCode,
            LocalDateTime createdAt,
            boolean makerCheckerBlockedForCurrentActor,
            boolean allTasksComplete,
            boolean staffResubmissionReady,
            List<TaskDto> tasks
    ) {
        public CorrectionRequestDto {
            tasks = List.copyOf(tasks);
        }
    }

    public record TaskDto(
            UUID taskId,
            String responsibleParty,
            String status,
            String scope,
            String documentType,
            UUID checklistItemId,
            UUID baselineDocumentVersionId,
            String reasonCode,
            String staffInstruction,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            String proofState
    ) {
    }
}
