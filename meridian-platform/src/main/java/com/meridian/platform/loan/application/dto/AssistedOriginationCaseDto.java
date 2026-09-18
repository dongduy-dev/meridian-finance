package com.meridian.platform.loan.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AssistedOriginationCaseDto(
        UUID assistedOriginationCaseId,
        String productCode,
        UUID customerId,
        String status,
        UUID loanApplicationId,
        UUID createdByStaffUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime terminalAt
) {
    public AssistedOriginationCaseDto(
            UUID assistedOriginationCaseId, String productCode, UUID customerId,
            String status, UUID createdByStaffUserId, LocalDateTime createdAt,
            LocalDateTime updatedAt, LocalDateTime terminalAt
    ) {
        this(assistedOriginationCaseId, productCode, customerId, status, null,
                createdByStaffUserId, createdAt, updatedAt, terminalAt);
    }
}
