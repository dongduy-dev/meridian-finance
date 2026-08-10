package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ApproveLoanSettlementRequest(
        @NotNull UUID requestId,
        @NotNull BigDecimal expectedSettlementAmount,
        @NotNull LocalDate paymentValueDate,
        @NotBlank String externalPaymentReference
) {
    @Override
    public String toString() {
        return "ApproveLoanSettlementRequest[paymentValueDate=" + paymentValueDate
                + ", settlementEvidence=redacted]";
    }
}
