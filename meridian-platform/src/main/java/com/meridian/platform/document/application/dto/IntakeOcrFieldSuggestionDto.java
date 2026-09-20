package com.meridian.platform.document.application.dto;

import java.math.BigDecimal;

public record IntakeOcrFieldSuggestionDto(
        String fieldName,
        String proposedValue,
        BigDecimal confidence
) {
}
