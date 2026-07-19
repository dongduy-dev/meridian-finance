package com.meridian.platform.document.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentReviewQueueItemDto(
        UUID checklistItemId,
        UUID loanApplicationId,
        String documentType,
        UUID currentVersionId,
        LocalDateTime uploadedAt,
        String uploaderActorType,
        String reviewStatus
) {
}
