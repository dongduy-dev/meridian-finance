package com.meridian.platform.document.application.dto;

import java.util.List;
import java.util.UUID;

public record CustomerDocumentChecklistDto(
        UUID checklistId,
        UUID loanApplicationId,
        String stage,
        boolean uploadComplete,
        boolean processingReady,
        List<ChecklistItemDto> items
) {
    public CustomerDocumentChecklistDto {
        items = List.copyOf(items);
    }

    public record ChecklistItemDto(
            UUID checklistItemId,
            String documentType,
            String requirementStatus,
            String customerStatus,
            boolean uploadComplete,
            boolean processingReady,
            DocumentVersionDto currentVersion
    ) {
    }
}
