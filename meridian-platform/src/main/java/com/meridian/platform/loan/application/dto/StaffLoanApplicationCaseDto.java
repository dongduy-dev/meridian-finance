package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffLoanApplicationCaseDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String productType,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        String status,
        LocalDateTime submittedAt,
        CustomerReadinessDto customerReadiness,
        List<LifecycleItemDto> lifecycleHistory
) {
    public StaffLoanApplicationCaseDto {
        lifecycleHistory = List.copyOf(lifecycleHistory);
    }

    public record CustomerReadinessDto(
            boolean active,
            boolean profileComplete,
            boolean hasPrimaryActiveBankAccount,
            String verificationStatus
    ) {
    }

    public record LifecycleItemDto(
            String fromStatus,
            String toStatus,
            String action,
            String actorType,
            LocalDateTime occurredAt
    ) {
    }
}
