package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;

public record AdminLoanProductDto(
        String productCode,
        String productType,
        String name,
        String description,
        boolean active,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {
}
