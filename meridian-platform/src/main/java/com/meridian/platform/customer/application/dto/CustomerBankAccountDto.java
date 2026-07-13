package com.meridian.platform.customer.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerBankAccountDto(
        UUID customerBankAccountId,
        String bankCode,
        String bankNameSnapshot,
        String accountHolderName,
        String maskedAccountNumber,
        String accountNumberLastFour,
        String status,
        boolean primaryAccount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deactivatedAt
) {
}
