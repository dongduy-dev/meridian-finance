package com.meridian.platform.document.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record IntakeOcrReviewDto(
        UUID ocrResultId,
        String evidenceType,
        String disposition,
        List<IntakeOcrFieldSuggestionDto> suggestions,
        Map<String, String> reviewedFields,
        LocalDateTime reviewedAt
) {
}
