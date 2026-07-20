package com.meridian.platform.document.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface DocumentReviewQueuePort {
    List<DocumentReviewQueueItem> findAwaitingReview(int offset, int limit);

    record DocumentReviewQueueItem(
            UUID checklistItemId,
            UUID loanApplicationId,
            String documentType,
            UUID currentVersionId,
            LocalDateTime uploadedAt,
            String uploaderActorType
    ) {
    }
}
