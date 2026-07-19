package com.meridian.platform.document.application.port.in;

import com.meridian.platform.document.application.dto.DocumentReviewQueueItemDto;

import java.util.List;

public interface QueryDocumentReviewQueueUseCase {
    List<DocumentReviewQueueItemDto> findAwaitingReview(int page, int size);
}
