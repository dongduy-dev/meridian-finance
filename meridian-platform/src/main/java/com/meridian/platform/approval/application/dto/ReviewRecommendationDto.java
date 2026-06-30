package com.meridian.platform.approval.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewRecommendationDto(
        UUID recommendationId,
        UUID loanApplicationId,
        UUID loanOfficerUserId,
        String action,
        String reason,
        String internalNotes,
        LocalDateTime submittedAt
) {
}
