package com.meridian.platform.document.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record OcrResult(
        UUID id,
        UUID ocrJobId,
        String encryptedStructuredSuggestions,
        OcrResultDisposition disposition,
        LocalDateTime createdAt
) {
    public OcrResult {
        Objects.requireNonNull(id);
        Objects.requireNonNull(ocrJobId);
        Objects.requireNonNull(encryptedStructuredSuggestions);
        Objects.requireNonNull(disposition);
        Objects.requireNonNull(createdAt);
    }

    public OcrResult reviewed() {
        return new OcrResult(id, ocrJobId, encryptedStructuredSuggestions,
                OcrResultDisposition.REVIEWED, createdAt);
    }
}
