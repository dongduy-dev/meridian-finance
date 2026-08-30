package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerLoanAccountSummaryDto(
        UUID loanApplicationId,
        UUID loanAccountId,
        String accountNumber,
        String applicationNumber,
        String productCode,
        String productType,
        String status,
        LocalDateTime activatedAt,
        BigDecimal originatedPrincipal,
        BigDecimal totalPaid,
        BigDecimal totalOutstanding,
        boolean servicingActive
) {
}
