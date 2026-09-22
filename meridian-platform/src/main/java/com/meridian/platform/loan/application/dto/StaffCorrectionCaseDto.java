package com.meridian.platform.loan.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffCorrectionCaseDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String originationChannel,
        String applicationStatus,
        CorrectionRequestDto correctionRequest,
        AssistedCancellationDto assistedCancellation
) {
    public record AssistedCancellationDto(
            boolean available,
            UUID correctionRequestId,
            AssistedActionEvidenceMetadataDto evidence,
            boolean evidenceUploadAvailable,
            boolean cancellationCommandAvailable
    ) {
    }

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
            String customerInstruction,
            String staffInstruction,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            String proofState,
            boolean customerSourceViaStaff,
            boolean uploadActionAvailable,
            boolean completionActionAvailable
    ) {
    }
}
