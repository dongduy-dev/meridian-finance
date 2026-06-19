package com.meridian.platform.partner.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PartnerCompany(
        UUID id,
        String companyCode,
        String name,
        PartnerCompanyStatus status,
        BigDecimal salaryAdvancePolicyLimit
) {
}