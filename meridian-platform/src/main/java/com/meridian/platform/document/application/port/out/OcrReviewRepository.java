package com.meridian.platform.document.application.port.out;

import com.meridian.platform.document.domain.model.OcrReview;

import java.util.Optional;
import java.util.UUID;

public interface OcrReviewRepository {

    Optional<OcrReview> findByOcrResultId(UUID resultId);

    OcrReview save(OcrReview review);
}
