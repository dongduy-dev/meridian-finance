package com.meridian.platform.customer.application.dto;

import java.util.UUID;

public record CustomerDto(
        UUID customerId,
        String customerNumber,
        String status,
        String verificationStatus,
        String profileCompletionStatus,
        boolean primaryActiveBankAccountPresent,
        CustomerProfileDto profile
) {
}
