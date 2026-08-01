package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecordRepaymentRequest(
        @NotNull UUID requestId,
        @NotBlank String externalPaymentReference,
        @NotNull BigDecimal amount,
        @NotNull LocalDate paymentValueDate
) {
    @Override
    public String toString() {
        return "RecordRepaymentRequest[requestId=" + requestId
                + ", externalPaymentReference=redacted, amount=redacted"
                + ", paymentValueDate=" + paymentValueDate + "]";
    }
}
