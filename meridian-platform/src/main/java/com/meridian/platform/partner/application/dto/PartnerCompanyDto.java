package com.meridian.platform.partner.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PartnerCompanyDto(
        UUID id,
        String companyCode,
        String name,
        String status,
        BigDecimal salaryAdvancePolicyLimit
) {
}