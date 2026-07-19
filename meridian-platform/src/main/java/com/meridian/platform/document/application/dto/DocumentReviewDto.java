package com.meridian.platform.document.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentReviewDto(
        UUID reviewDecisionId,
        UUID checklistItemId,
        UUID documentVersionId,
        String outcome,
        String waiverReasonCode,
        LocalDateTime decidedAt
) {
}
