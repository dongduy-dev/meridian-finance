package com.meridian.platform.loan.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record LoanProduct(
        UUID id,
        ProductCode productCode,
        ProductType productType,
        String name,
        String description,
        boolean active,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {
}