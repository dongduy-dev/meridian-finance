package com.meridian.platform.loan.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record LoanApplication(
        UUID id,
        UUID customerId,
        UUID loanProductId,
        String applicationNumber,
        ProductCode productCode,
        ProductType productType,
        LoanApplicationStatus status,
        BigDecimal requestedAmount,
        int requestedTermMonths,
        LocalDateTime submittedAt
) {

    public static LoanApplication submitted(
            UUID id,
            UUID customerId,
            LoanProduct loanProduct,
            String applicationNumber,
            BigDecimal requestedAmount,
            int requestedTermMonths,
            LocalDateTime submittedAt
    ) {
        Objects.requireNonNull(loanProduct, "loanProduct must not be null");

        return new LoanApplication(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(customerId, "customerId must not be null"),
                loanProduct.id(),
                Objects.requireNonNull(applicationNumber, "applicationNumber must not be null"),
                loanProduct.productCode(),
                loanProduct.productType(),
                LoanApplicationStatus.SUBMITTED,
                Objects.requireNonNull(requestedAmount, "requestedAmount must not be null"),
                requestedTermMonths,
                Objects.requireNonNull(submittedAt, "submittedAt must not be null")
        );
    }
}
