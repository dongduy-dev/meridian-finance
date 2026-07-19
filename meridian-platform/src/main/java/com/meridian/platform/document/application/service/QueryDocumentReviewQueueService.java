package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.DocumentReviewQueueItemDto;
import com.meridian.platform.document.application.port.in.QueryDocumentReviewQueueUseCase;
import com.meridian.platform.document.application.port.out.DocumentReviewQueuePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QueryDocumentReviewQueueService implements QueryDocumentReviewQueueUseCase {
    private final DocumentReviewQueuePort reviewQueuePort;

    public QueryDocumentReviewQueueService(DocumentReviewQueuePort reviewQueuePort) {
        this.reviewQueuePort = reviewQueuePort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentReviewQueueItemDto> findAwaitingReview(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Document review queue page must be non-negative and size 1 through 100.");
        }
        return reviewQueuePort.findAwaitingReview(page * size, size).stream()
                .map(item -> new DocumentReviewQueueItemDto(
                        item.checklistItemId(), item.loanApplicationId(), item.documentType(),
                        item.currentVersionId(), item.uploadedAt(), item.uploaderActorType(), "AWAITING_REVIEW"
                )).toList();
    }
}
