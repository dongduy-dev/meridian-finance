package com.meridian.platform.document.infrastructure.adapter.in.web;

import com.meridian.platform.document.application.dto.DocumentReviewQueueItemDto;
import com.meridian.platform.document.application.port.in.QueryDocumentReviewQueueUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/document-review-items")
public class DocumentReviewQueueController {
    private final QueryDocumentReviewQueueUseCase queryUseCase;

    public DocumentReviewQueueController(QueryDocumentReviewQueueUseCase queryUseCase) {
        this.queryUseCase = queryUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('document:review')")
    public List<DocumentReviewQueueItemDto> findAwaitingReview(
            @RequestParam(defaultValue = "AWAITING_REVIEW") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        if (!"AWAITING_REVIEW".equals(status)) {
            throw new IllegalArgumentException("Only AWAITING_REVIEW status is supported.");
        }
        return queryUseCase.findAwaitingReview(page, size);
    }
}
