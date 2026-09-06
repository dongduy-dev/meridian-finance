package com.meridian.platform.approval.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffApprovalQueuePageDto(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ItemDto> items
) {
    public StaffApprovalQueuePageDto {
        items = List.copyOf(items);
    }

    public record ItemDto(
            UUID loanApplicationId,
            String applicationNumber,
            String productCode,
            String productType,
            BigDecimal requestedAmount,
            int requestedTermMonths,
            String applicationStatus,
            LocalDateTime submittedAt,
            UUID recommendationId,
            String recommendationAction,
            LocalDateTime recommendationSubmittedAt,
            boolean makerCheckerEligible,
            boolean decisionAvailable
    ) {
    }
}
