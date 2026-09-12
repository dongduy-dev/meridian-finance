package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffServicingWorkPageDto(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ItemDto> items
) {
    public StaffServicingWorkPageDto {
        items = List.copyOf(items);
    }

    public record ItemDto(
            UUID loanApplicationId,
            UUID loanAccountId,
            String applicationNumber,
            String accountNumber,
            String productCode,
            String productType,
            String accountStatus,
            LocalDateTime activatedAt,
            BigDecimal originatedPrincipal,
            BigDecimal totalPaid,
            BigDecimal totalOutstanding,
            LocalDate servicingEvaluationDate,
            LocalDate lastPaymentValueDate,
            LocalDateTime lastPaymentRecordedAt
    ) {
    }
}
