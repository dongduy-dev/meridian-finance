package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffLoanApplicationPageDto(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ItemDto> items
) {
    public StaffLoanApplicationPageDto {
        items = List.copyOf(items);
    }

    public record ItemDto(
            UUID loanApplicationId,
            String applicationNumber,
            String productCode,
            String productType,
            BigDecimal requestedAmount,
            int requestedTermMonths,
            String status,
            LocalDateTime submittedAt
    ) {
    }
}
