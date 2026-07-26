package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;

public record LoanContractRepaymentItemDto(
        int installmentNumber,
        BigDecimal principalDue,
        BigDecimal interestDue,
        BigDecimal feeDue,
        BigDecimal totalDue
) {
}
