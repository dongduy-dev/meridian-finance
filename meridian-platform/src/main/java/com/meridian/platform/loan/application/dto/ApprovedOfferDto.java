package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ApprovedOfferDto(
        UUID approvedOfferId,
        UUID loanApplicationId,
        String status,
        BigDecimal approvedPrincipal,
        int approvedTermMonths,
        String interestCalculationMethod,
        BigDecimal flatMonthlyInterestRate,
        BigDecimal totalInterest,
        BigDecimal feeAmount,
        BigDecimal totalRepaymentAmount,
        String repaymentMethod,
        LocalDateTime generatedAt,
        LocalDateTime expiresAt,
        LocalDateTime acceptedAt,
        LocalDateTime declinedAt,
        LocalDateTime expiredAt,
        List<String> availableActions,
        List<ProvisionalRepaymentItemDto> repaymentItems
) {
}
