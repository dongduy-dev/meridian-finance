package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;

public record CustomerCollateralDto(
        String collateralType,
        String description,
        BigDecimal estimatedValue,
        String ownershipStatus,
        String conditionNote
) {
}
