package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record UnsecuredConsumerLoanApplicationDto(
        UUID loanApplicationId,
        String applicationNumber,
        String productCode,
        String productType,
        String status,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        String productVerificationResult,
        LocalDateTime submittedAt
) {
}
