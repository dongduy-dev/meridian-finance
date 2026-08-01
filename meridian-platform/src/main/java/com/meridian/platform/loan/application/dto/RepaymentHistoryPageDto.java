package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RepaymentHistoryPageDto(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ItemDto> items
) {
    public RepaymentHistoryPageDto {
        items = List.copyOf(items);
    }

    public record ItemDto(
            UUID repaymentTransactionId,
            BigDecimal receivedAmount,
            LocalDate paymentValueDate,
            LocalDateTime recordedAt,
            BigDecimal principalAllocated,
            BigDecimal principalReleased,
            String resultingLoanAccountStatus,
            RecordRepaymentDto.AccountBalanceDto accountBalance,
            List<RecordRepaymentDto.AllocationDto> allocations,
            List<RecordRepaymentDto.InstallmentOutcomeDto> affectedInstallments
    ) {
        public ItemDto {
            allocations = List.copyOf(allocations);
            affectedInstallments = List.copyOf(affectedInstallments);
        }

        @Override
        public String toString() {
            return "ItemDto[repaymentTransactionId=" + repaymentTransactionId
                    + ", financialAndActorEvidence=redacted]";
        }
    }
}
