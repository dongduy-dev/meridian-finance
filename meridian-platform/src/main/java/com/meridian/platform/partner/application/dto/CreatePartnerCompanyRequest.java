package com.meridian.platform.partner.application.dto;

import com.meridian.platform.partner.domain.model.PartnerCompanyStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePartnerCompanyRequest(
        @NotBlank @Size(max = 50) String companyCode,
        @NotBlank @Size(max = 200) String name,
        @NotNull PartnerCompanyStatus status,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal salaryAdvancePolicyLimit
) {
}
