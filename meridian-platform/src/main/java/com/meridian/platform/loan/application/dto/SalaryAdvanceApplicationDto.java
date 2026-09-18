package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SalaryAdvanceApplicationDto(
        UUID loanApplicationId,
        String applicationNumber,
        UUID customerId,
        String productCode,
        String productType,
        String originationChannel,
        String status,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        UUID customerPartnerEmployeeLinkId,
        String productVerificationResult,
        BigDecimal totalLimitSnapshot,
        BigDecimal usedAmountSnapshot,
        BigDecimal reservedAmountSnapshot,
        BigDecimal availableLimitSnapshot,
        LocalDateTime submittedAt
) {
    public SalaryAdvanceApplicationDto(
            UUID loanApplicationId, String applicationNumber, UUID customerId,
            String productCode, String productType, String status,
            BigDecimal requestedAmount, int requestedTermMonths,
            UUID customerPartnerEmployeeLinkId, String productVerificationResult,
            BigDecimal totalLimitSnapshot, BigDecimal usedAmountSnapshot,
            BigDecimal reservedAmountSnapshot, BigDecimal availableLimitSnapshot,
            LocalDateTime submittedAt
    ) {
        this(loanApplicationId, applicationNumber, customerId, productCode, productType,
                "CUSTOMER_DIGITAL", status, requestedAmount, requestedTermMonths,
                customerPartnerEmployeeLinkId, productVerificationResult, totalLimitSnapshot,
                usedAmountSnapshot, reservedAmountSnapshot, availableLimitSnapshot, submittedAt);
    }
}
