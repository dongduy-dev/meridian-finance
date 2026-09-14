package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record StaffApprovedSettlementEvidenceDto(
        UUID loanApplicationId,
        UUID loanAccountId,
        BigDecimal settlementAmount,
        LocalDate paymentValueDate,
        LocalDateTime approvedAt
) {
}
