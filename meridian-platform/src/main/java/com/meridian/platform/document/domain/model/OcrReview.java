package com.meridian.platform.document.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record OcrReview(
        UUID id,
        UUID ocrResultId,
        UUID reviewerStaffUserId,
        OcrResultDisposition sourceDisposition,
        String encryptedReviewedFields,
        LocalDateTime reviewedAt
) {
    public OcrReview {
        Objects.requireNonNull(id);
        Objects.requireNonNull(ocrResultId);
        Objects.requireNonNull(reviewerStaffUserId);
        Objects.requireNonNull(sourceDisposition);
        Objects.requireNonNull(encryptedReviewedFields);
        Objects.requireNonNull(reviewedAt);
        if (sourceDisposition == OcrResultDisposition.REVIEWED) {
            throw new IllegalArgumentException("A review must preserve the pre-review disposition.");
        }
    }
}
