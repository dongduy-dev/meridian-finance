package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffDisbursementWorkPageDto(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ItemDto> items
) {
    public StaffDisbursementWorkPageDto {
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
            StaffDisbursementContractDto currentContract,
            String workStage
    ) {
    }
}
