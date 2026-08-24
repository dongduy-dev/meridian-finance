package com.meridian.platform.loan.application.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.meridian.platform.loan.domain.model.collateral.CollateralType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CollateralDetailsRequest(
        @NotNull CollateralType type,
        @NotBlank @Size(max = 500) String description,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal estimatedValue,
        @NotBlank @Size(max = 200) String ownershipStatus,
        @NotBlank @Size(max = 500) String conditionNote
) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Unsupported collateral details field.");
    }
}
