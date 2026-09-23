package com.meridian.platform.partner.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PartnerEligibilityReviewPageDto(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<Item> items
) {
    public PartnerEligibilityReviewPageDto {
        items = List.copyOf(items);
    }

    public record Item(
            UUID reviewId,
            UUID customerId,
            UUID partnerCompanyId,
            String partnerCompanyCode,
            String partnerCompanyName,
            String effectiveMonth,
            String triggerOutcome,
            String requestedEmployeeCode,
            String status,
            LocalDateTime createdAt,
            boolean reviewable,
            String nonReviewableReason
    ) {
    }
}
