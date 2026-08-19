package com.meridian.platform.loan.application.dto;

import java.math.BigDecimal;

public record CollateralAssessmentSnapshotDto(
        String collateralType,
        String description,
        BigDecimal estimatedValue,
        String ownershipStatus,
        String conditionNote
) {
}
