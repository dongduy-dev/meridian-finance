package com.meridian.platform.document.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffDocumentChecklistDto(
        UUID loanApplicationId,
        String applicationStatus,
        String checklistStage,
        boolean uploadComplete,
        boolean processingReady,
        List<ChecklistItemDto> items
) {
    public StaffDocumentChecklistDto {
        items = List.copyOf(items);
    }

    public record ChecklistItemDto(
            UUID checklistItemId,
            String documentType,
            String requirementStatus,
            String evidenceStatus,
            boolean uploadComplete,
            boolean processingReady,
            VersionDto currentVersion,
            List<VersionDto> versionHistory,
            List<ReviewDto> reviewHistory
    ) {
        public ChecklistItemDto {
            versionHistory = List.copyOf(versionHistory);
            reviewHistory = List.copyOf(reviewHistory);
        }
    }

    public record VersionDto(
            UUID documentVersionId,
            int versionNumber,
            String originalFilename,
            String detectedMimeType,
            long byteSize,
            LocalDateTime uploadedAt
    ) {
    }

    public record ReviewDto(
            UUID documentVersionId,
            String outcome,
            String waiverReasonCode,
            LocalDateTime decidedAt
    ) {
    }
}
